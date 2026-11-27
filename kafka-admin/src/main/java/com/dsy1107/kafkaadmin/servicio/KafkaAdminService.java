package com.dsy1107.kafkaadmin.servicio;

import com.dsy1107.kafkaadmin.api.Peticiones;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

// Las operaciones de administracion de Kafka, con el AdminClient:
// topicos (crear, listar, describir, cambiar configuracion, aumentar particiones,
// eliminar) y consumer groups (listar e inspeccionar offsets y lag).
//
// Cada error de Kafka se traduce a un ErrorKafka con su codigo HTTP.
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

    private final AdminClient admin;

    public KafkaAdminService(AdminClient admin) {
        this.admin = admin;
    }

    // ---------- Topicos ----------

    public List<String> listarTopicos() {
        return esperar(admin.listTopics().names()).stream().sorted().toList();
    }

    public Map<String, Object> describirTopico(String nombre) {
        TopicDescription d = esperar(admin.describeTopics(List.of(nombre)).allTopicNames()).get(nombre);
        List<Map<String, Object>> particiones = d.partitions().stream().map(p -> Map.<String, Object>of(
                "particion", p.partition(),
                "lider", p.leader() == null ? -1 : p.leader().id(),
                "replicas", ids(p.replicas()),
                "replicasAlDia", ids(p.isr())))
            .toList();

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("nombre", d.name());
        respuesta.put("particiones", particiones.size());
        respuesta.put("replicas", d.partitions().get(0).replicas().size());
        respuesta.put("configuraciones", configuracionesPropias(nombre));
        respuesta.put("detalle", particiones);
        return respuesta;
    }

    public void crearTopico(Peticiones.CrearTopico p) {
        NewTopic topico = new NewTopic(p.nombre(), p.particiones(), p.replicas().shortValue());
        if (p.configuraciones() != null) {
            topico.configs(p.configuraciones());
        }
        esperar(admin.createTopics(List.of(topico)).all());
        log.info("Topico creado: {} ({} particiones, {} replicas, {})",
            p.nombre(), p.particiones(), p.replicas(), p.configuraciones());
    }

    public Map<String, String> cambiarConfiguraciones(String nombre, Map<String, String> cambios) {
        describirTopico(nombre); // 404 si no existe
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        List<AlterConfigOp> operaciones = cambios.entrySet().stream()
            .map(e -> new AlterConfigOp(new ConfigEntry(e.getKey(), e.getValue()), AlterConfigOp.OpType.SET))
            .toList();
        esperar(admin.incrementalAlterConfigs(Map.of(recurso, operaciones)).all());
        log.info("Configuracion cambiada en {}: {}", nombre, cambios);
        return configuracionesPropias(nombre);
    }

    public void aumentarParticiones(String nombre, int particiones) {
        esperar(admin.createPartitions(Map.of(nombre, NewPartitions.increaseTo(particiones))).all());
        log.info("Particiones de {} aumentadas a {}", nombre, particiones);
    }

    public void eliminarTopico(String nombre) {
        esperar(admin.deleteTopics(List.of(nombre)).all());
        log.info("Topico eliminado: {}", nombre);
    }

    // Solo las configuraciones puestas para este topico (no las que hereda del broker).
    private Map<String, String> configuracionesPropias(String nombre) {
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        Config config = esperar(admin.describeConfigs(List.of(recurso)).all()).get(recurso);
        return config.entries().stream()
            .filter(e -> e.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG)
            .collect(Collectors.toMap(ConfigEntry::name, ConfigEntry::value, (a, b) -> a, TreeMap::new));
    }

    // ---------- Consumer groups ----------

    public List<Map<String, Object>> listarGrupos() {
        List<String> ids = esperar(admin.listGroups().all()).stream()
            .map(g -> g.groupId())
            .sorted()
            .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, ConsumerGroupDescription> grupos = esperar(admin.describeConsumerGroups(ids).all());
        return ids.stream().map(id -> Map.<String, Object>of(
                "grupo", id,
                "estado", grupos.get(id).groupState().toString(),
                "consumidores", grupos.get(id).members().size()))
            .toList();
    }

    // El estado del grupo, sus consumidores y, por cada particion: offset confirmado,
    // ultimo offset del topico y lag (cuantos eventos le faltan por leer).
    public Map<String, Object> describirGrupo(String grupo) {
        ConsumerGroupDescription d = esperar(admin.describeConsumerGroups(List.of(grupo)).all()).get(grupo);
        Map<TopicPartition, OffsetAndMetadata> confirmados =
            esperar(admin.listConsumerGroupOffsets(grupo).partitionsToOffsetAndMetadata());
        if (d.groupState() == GroupState.DEAD && confirmados.isEmpty()) {
            throw new ErrorKafka(HttpStatus.NOT_FOUND, "El consumer group '" + grupo + "' no existe");
        }

        Map<TopicPartition, OffsetSpec> pedirUltimos = confirmados.keySet().stream()
            .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ultimos =
            esperar(admin.listOffsets(pedirUltimos).all());

        List<Map<String, Object>> particiones = confirmados.entrySet().stream()
            .sorted(Map.Entry.comparingByKey((a, b) -> a.topic().equals(b.topic())
                ? Integer.compare(a.partition(), b.partition()) : a.topic().compareTo(b.topic())))
            .map(e -> {
                long confirmado = e.getValue() == null ? 0 : e.getValue().offset();
                long ultimo = ultimos.get(e.getKey()).offset();
                return Map.<String, Object>of(
                    "topico", e.getKey().topic(),
                    "particion", e.getKey().partition(),
                    "offsetConfirmado", confirmado,
                    "ultimoOffset", ultimo,
                    "lag", ultimo - confirmado);
            })
            .toList();

        List<Map<String, Object>> consumidores = d.members().stream().map(m -> Map.<String, Object>of(
                "consumidor", m.consumerId(),
                "host", m.host(),
                "particiones", m.assignment().topicPartitions().stream()
                    .map(tp -> tp.topic() + "-" + tp.partition()).sorted().toList()))
            .toList();

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("grupo", grupo);
        respuesta.put("estado", d.groupState().toString());
        respuesta.put("lagTotal", particiones.stream().mapToLong(p -> (Long) p.get("lag")).sum());
        respuesta.put("consumidores", consumidores);
        respuesta.put("particiones", particiones);
        return respuesta;
    }

    // ---------- Traduccion de errores ----------

    private static List<Integer> ids(Collection<Node> nodos) {
        return nodos.stream().map(Node::id).toList();
    }

    private static <T> T esperar(KafkaFuture<T> futuro) {
        try {
            return futuro.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorKafka(HttpStatus.SERVICE_UNAVAILABLE, "Operacion interrumpida");
        } catch (ExecutionException e) {
            throw traducir(e.getCause());
        }
    }

    private static ErrorKafka traducir(Throwable c) {
        if (c instanceof UnknownTopicOrPartitionException) {
            return new ErrorKafka(HttpStatus.NOT_FOUND, "El topico no existe");
        }
        if (c instanceof GroupIdNotFoundException) {
            return new ErrorKafka(HttpStatus.NOT_FOUND, "El consumer group no existe");
        }
        if (c instanceof TopicExistsException) {
            return new ErrorKafka(HttpStatus.CONFLICT, "El topico ya existe");
        }
        if (c instanceof InvalidPartitionsException || c instanceof InvalidReplicationFactorException
                || c instanceof InvalidConfigurationException || c instanceof InvalidRequestException
                || c instanceof InvalidTopicException) {
            return new ErrorKafka(HttpStatus.BAD_REQUEST, c.getMessage());
        }
        if (c instanceof TimeoutException) {
            return new ErrorKafka(HttpStatus.SERVICE_UNAVAILABLE, "Ningun broker de Kafka responde");
        }
        log.error("Error inesperado de Kafka", c);
        return new ErrorKafka(HttpStatus.INTERNAL_SERVER_ERROR, String.valueOf(c.getMessage()));
    }
}

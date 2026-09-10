# Hosting del front (sitio estático sobre CDN, con HTTPS y dominio incluidos).
resource "aws_amplify_app" "front" {
  name     = "dsy1107-${var.estudiante}"
  platform = "WEB"

  # Una SPA sirve index.html para CUALQUIER ruta; sin esto, todo lo que no sea
  # "/" da 404. El "json" en la lista va A PROPOSITO: si no, la regla se
  # tragaria /config.json y el front arrancaria sin configuracion.
  custom_rule {
    source = "</^[^.]+$|\\.(?!(css|gif|ico|jpg|js|png|txt|svg|woff|woff2|ttf|map|json|webp)$)([^.]+$)/>"
    target = "/index.html"
    status = "200"
  }
}

resource "aws_amplify_branch" "main" {
  app_id      = aws_amplify_app.front.id
  branch_name = "main"
  framework   = "React"
  stage       = "PRODUCTION"
}

locals {
  url_amplify = "https://${aws_amplify_branch.main.branch_name}.${aws_amplify_app.front.default_domain}"
}

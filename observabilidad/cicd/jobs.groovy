def servicios = [
  [nombre: 'gestion-empleados',  ruta: 'microservicios/gestion-empleados'],
  [nombre: 'autenticacion',       ruta: 'microservicios/autenticacion'],
  [nombre: 'gestion-perfiles',    ruta: 'microservicios/gestion-perfiles'],
  [nombre: 'gestion-vacaciones',  ruta: 'microservicios/gestion-vacaciones'],
  [nombre: 'notificaciones',      ruta: 'microservicios/notificaciones'],
  [nombre: 'api-gateway',         ruta: 'microservicios/api-gateway'],
]

servicios.each { svc ->
  pipelineJob(svc.nombre) {
    description("Pipeline CI/CD para ${svc.nombre}")
    definition {
      cpsScm {
        scm {
          git {
            remote {
              url('https://github.com/MiguelMontenegroG/py_microservicios')
              branch('*/master')
            }
          }
        }
        scriptPath("${svc.ruta}/Jenkinsfile")
        lightweight(true)
      }
    }
    triggers {
      scm('H/5 * * * *')
    }
  }
}

# Diario de desarrollo

## 16 de octubre de 2026 - Entorno de desarrollo

- **Problema:** tras instalar el JDK 25 (Temurin), `java -version` seguía mostrando la 21.
- **Causa:** el fichero `~/.zshrc` forzaba la versión 21 con `JAVA_HOME=$(/usr/libexec/java_home -v 21)`, y además tenía esas líneas repetidas tres veces.
- **Solución:** sustituir `-v 21` por `-v 25` con `sed`, tras hacer una copia de seguridad del fichero.
- **Decisión:** se usa Java 25 por ser LTS y coincidir con JavaFX 25 (ver apartado 7.1.1 de la memoria).

## 21 de octubre de 2026 - Proyecto base de la API

- Se genera el proyecto con Spring Initializr: Spring Boot 4.1.1, Java 25, Maven y las dependencias Web, Data JPA, Security, Validation y H2.
- **Observación:** en Spring Boot 4 los starters tienen nombres distintos a los de la mayoría de tutoriales (por ejemplo, `spring-boot-starter-webmvc` en lugar de `spring-boot-starter-web`), y la consola de H2 es un módulo aparte (`spring-boot-h2console`). Hay que tenerlo en cuenta al consultar documentación antigua.
- **Decisión:** la base de datos H2 se configura en modo fichero (`jdbc:h2:file:./data/facturacion`) para que los datos persistan entre ejecuciones.
- **Problema:** al comprobar `git status`, la carpeta `data/` (con la base de datos) iba a subirse al repositorio público.
- **Solución:** añadir `data/` y `.DS_Store` al `.gitignore` antes del primer commit.
- **Problema:** Initializr creó el paquete base como `facturacion_api`, distinto del `com.facturacion.api` definido en el diseño.
- **Solución:** mover las clases con `git mv` y corregir la línea `package`, haciéndolo antes de escribir código para evitar un cambio mayor después.
- **Resultado:** la API arranca correctamente (Tomcat 11, Hibernate 7.4.5, H2 2.4.240) y el login por defecto de Spring Security aparece en `localhost:8080`.
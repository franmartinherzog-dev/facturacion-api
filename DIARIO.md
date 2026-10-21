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

## 21 de octubre de 2026 (tarde) - Entidad User y su repositorio

- **Decisión:** el código (clases, campos, paquetes) se escribe en inglés (`User`, `companyName`, `taxId`, `address`), mientras que la memoria está redactada en español. Hay que mantener una equivalencia clara entre ambos al citar clases en la memoria.
- **Decisión:** `GenerationType.IDENTITY` para que sea la base de datos la que genere los identificadores, y constructor `protected` sin argumentos en la entidad porque JPA lo exige.
- **Decisión:** el campo `password` almacena únicamente el hash BCrypt, nunca la contraseña en claro.
- **Problema:** la tabla de usuarios no puede llamarse `user`, porque es palabra reservada en H2 y fallaría al crear la tabla.
- **Solución:** `@Table(name = "users")`.
- **Problema:** la primera versión de `User.java` no compilaba: el import de `jakarta.persistence` estaba mal escrito (`persistance`), el NIF se había llamado `id` (duplicando la clave primaria) y el constructor no coincidía con el nombre de la clase.
- **Solución:** imports explícitos en lugar de comodín, campo renombrado a `taxId` y clase, fichero y constructor con el mismo nombre. Con el comodín el error de escritura pasaba desapercibido hasta compilar.
- **Resultado:** creados `User` y `UserRepository` (con `findByUsername` y `existsByUsername`, que se usarán en el registro y el login).

- **Decisión:** se activa `spring.jpa.show-sql=true` en `application.properties`. Con `ddl-auto=update` Hibernate crea las tablas a partir de las entidades pero sin mostrar nada, así que no hay forma de saber si el esquema generado es el esperado. Esta propiedad imprime en el log cada sentencia SQL ejecutada y sirve como herramienta de depuración durante el desarrollo.
- **Comprobación:** al arrancar la API con `./mvnw spring-boot:run`, el log muestra `create table users (...)` con las columnas `id`, `username`, `password`, `company_name`, `tax_id` y `address`, los campos obligatorios como `not null` y una restricción `unique` sobre `username`. El esquema coincide con el diseño.


## 21 de octubre de 2026 (tarde) - Entidad Client

- **Decisión:** la relación entre cliente y usuario se modela con `@ManyToOne` y `@JoinColumn(name = "user_id")` en `Client`. Es el lado "muchos" de la relación 1:N del diagrama Entidad-Relación y garantiza que cada cliente pertenece a un único usuario. Se marca como obligatoria (`optional = false`, `nullable = false`) para que no puedan existir clientes sin dueño.
- **Decisión:** `FetchType.LAZY` para que Hibernate no cargue el usuario completo cada vez que carga un cliente, sino solo cuando se necesite. Reduce consultas innecesarias y contribuye al requisito de rendimiento.
- **Decisión:** `Client` tiene setters para sus datos editables (necesarios para la edición del CRUD), pero no para `user`, porque un cliente no puede cambiar de propietario.
- **Comprobación:** al arrancar, el log muestra `create table clients (...)` con la columna `user_id bigint not null` y una restricción de clave foránea hacia `users`. El esquema coincide con el diseño.
- **Observación:** el arranque muestra que Spring Boot encuentra `1 JPA repository interface` (`UserRepository`); `Client` todavía no tiene repositorio.

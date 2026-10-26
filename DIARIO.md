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


## 21 de octubre de 2026 (tarde) - Repositorio de clientes

- **Decisión:** `ClientRepository` solo ofrece consultas que incluyen al usuario propietario (`findByUser`, `findByIdAndUser` y `findByUserAndNameContainingIgnoreCase`). Nunca se busca un cliente solo por su identificador: así, si un usuario pide un cliente ajeno, simplemente no se encuentra, lo que aplica de forma estructural el requisito de aislamiento de datos entre usuarios (RNF de seguridad).
- **Observación:** Spring Data JPA genera la consulta a partir del nombre del método, por lo que un nombre incorrecto (por ejemplo `findByUser(Long id, User user)` en lugar de `findByIdAndUser`) no es un simple fallo de estilo: la consulta no se corresponde con los parámetros y la aplicación falla al arrancar.
- **Decisión:** se descarta `existsByUser`, porque comprobar si un cliente tiene facturas (para impedir su baja) corresponde al repositorio de facturas, que se creará más adelante.
- **Comprobación:** tras corregir el repositorio, la API arranca sin errores y el log indica `Found 2 JPA repository interfaces` (`UserRepository` y `ClientRepository`). Spring Data valida los nombres de los métodos al arrancar, por lo que esta comprobación confirma que las tres consultas son correctas.


## 22 de octubre de 2026 - Entidad Invoice

- **Decisión:** los importes (`taxableBase`, `vatAmount`, `irpfAmount`, `total`) y los tipos impositivos se definen como `BigDecimal` con `precision = 12, scale = 2` (`precision = 5, scale = 2` para los tipos), que se traduce a `decimal(12,2)` en la base de datos. Con `double` aparecen errores de representación (por ejemplo `0.1 + 0.2 = 0.30000000000000004`), inadmisibles al manejar dinero (RNF-20).
- **Decisión:** el número de factura es único por usuario, no global, mediante `@UniqueConstraint` sobre `(user_id, number)`. Así cada usuario tiene su propia numeración correlativa con formato AAAA-NNNN.
- **Decisión:** `Invoice` no tiene setters: una factura emitida no se modifica, lo que es coherente con la trazabilidad que se plantea en la introducción. Los importes los calcula siempre la API.
- **Decisión:** `Invoice` se crea primero sin la lista de líneas, porque `InvoiceLine` todavía no existe; la relación `@OneToMany` con borrado en cascada se añadirá cuando se cree esa entidad, para que cada paso compile.
- **Problema:** la primera versión tenía el nombre de columna mal escrito en la restricción única (`"numer"` en lugar de `"number"`), de modo que apuntaba a una columna inexistente. Además, el constructor no asignaba `vatRate`; este error compila y arranca sin avisar, pero habría dejado el IVA a `null` en todas las facturas y habría fallado al guardar la primera.
- **Solución:** corregir ambos puntos. **Lección:** los fallos de constructor no los detecta el compilador ni el arranque; se descubrirán con las pruebas unitarias de la creación de facturas.
- **Comprobación:** al arrancar, el log muestra `create table invoices (...)` con los importes como `numeric(12,2)` y los tipos impositivos como `numeric(5,2)` (H2 usa `numeric`, equivalente a `decimal`), la restricción `unique (user_id, number)` y dos claves foráneas, hacia `clients` y hacia `users`. El esquema coincide con el diagrama Entidad-Relación.


## 22 de octubre de 2026 - Entidad InvoiceLine

- **Decisión:** `getSubtotal()` (cantidad × precio unitario) no se almacena en la base de datos, sino que se calcula cuando se necesita. Hibernate lee los campos de la entidad (la anotación `@Id` está sobre un campo), no los getters, así que no genera columna para él. De este modo el subtotal nunca puede quedar desincronizado respecto a la cantidad y el precio.
- **Decisión:** `getSubtotal()` devuelve el producto exacto, sin redondear. El redondeo a dos decimales se aplicará una única vez sobre la suma al calcular los totales de la factura, tal como indica la fórmula de la memoria (base imponible = Σ cantidad × precio unitario). Redondear cada línea y luego sumar podría dar diferencias de céntimos.
- **Decisión:** la cantidad es `BigDecimal` con dos decimales (`precision = 10, scale = 2`) para admitir cantidades como 1,5 horas.
- **Comprobación:** al arrancar, el log muestra `create table invoice_lines (...)` con `quantity numeric(10,2)`, `unit_price numeric(12,2)` e `invoice_id bigint not null`, y una clave foránea hacia `invoices`. Con esta entidad quedan creadas las cuatro tablas del diagrama Entidad-Relación.


## 22 de octubre de 2026 (tarde) - Relación Invoice - InvoiceLine con borrado en cascada

- **Decisión:** `Invoice` incorpora la lista de líneas con `@OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)`. `mappedBy` indica que la clave foránea ya existe en `invoice_lines.invoice_id`, por lo que no se crean tablas ni columnas nuevas. `CascadeType.ALL` hace que al guardar una factura se guarden sus líneas y al borrarla se borren también (restricción del apartado 5.2.2 de la memoria), y `orphanRemoval` elimina de la base de datos las líneas que se quiten de la lista.
- **Decisión:** el método `addLine(descripción, cantidad, precio)` crea la línea ya enlazada a su factura, para que no pueda olvidarse de enlazarla.
- **Problema:** al añadir los imports nuevos, VS Code los eliminaba al guardar, porque todavía no se usaban en el código (opción de organizar imports al guardar).
- **Solución:** escribir primero el campo y los métodos, y añadir después los imports con la corrección rápida del editor (`Cmd + .`), ya con las clases en uso.
- **Comprobación:** la API arranca sin errores (`Started FacturacionApiApplication`) y no se genera ninguna tabla nueva, como se esperaba. Con esto queda completo el modelo de dominio de la API: `User`, `Client`, `Invoice` e `InvoiceLine`.


## 23 de octubre de 2026 - Dependencias de JWT

- **Decisión:** se añade JJWT 0.13.0 al `pom.xml` con tres módulos: `jjwt-api` (interfaces que usa el código propio), `jjwt-impl` (implementación, en ámbito `runtime` porque el código nunca la usa directamente) y `jjwt-gson` (serialización del contenido del token a JSON, también `runtime`).
- **Decisión:** se usa `jjwt-gson` en lugar de `jjwt-jackson` porque este último depende de Jackson 2, mientras que Spring Boot 4 trae Jackson 3, y mezclar ambos podría provocar conflictos de versiones (ver apartado 7.1.1 de la memoria).
- **Comprobación:** `./mvnw compile` descarga las tres librerías y termina con `BUILD SUCCESS`, lo que confirma que la versión 0.13.0 existe y es compatible con el resto de dependencias.
- **Observación:** OpenPDF no se añade todavía; se incorporará en la tarea de generación de PDF (T15), para no cargar dependencias que aún no se usan.


## 23 de octubre de 2026 (mediodía) - Configuración de la clave JWT

- **Problema:** los tokens JWT se firman con una clave secreta; quien la conozca puede fabricar tokens válidos para cualquier usuario. El repositorio de GitHub es público, por lo que la clave no puede escribirse en `application.properties`.
- **Solución:** la clave se lee de la variable de entorno `JWT_SECRET` mediante `app.jwt.secret=${JWT_SECRET}`. Se genera con `openssl rand -base64 48` y no se guarda en ningún fichero del proyecto. Esto forma parte de las medidas de protección frente a vulnerabilidades comunes (RNF de seguridad) y habrá que explicarlo en el manual de instalación (Anexo I): quien despliegue la API debe definir `JWT_SECRET` antes de arrancarla.
- **Decisión:** no se define un valor por defecto para la clave. Si la variable no existe, la API debe negarse a arrancar con un error claro, en lugar de funcionar silenciosamente con una clave insegura.
- **Decisión:** la caducidad del token se fija en `app.jwt.expiration-ms=3600000` (1 hora), configurable sin tocar el código.
- **Observación:** el arranque correcto de la API todavía no demuestra que la variable se lee bien, porque Spring no resuelve una propiedad hasta que algún componente la utiliza. La comprobación real se hará al crear `JwtService`.


## 23 de octubre de 2026 - JwtService

- **Decisión:** `JwtService` (paquete `security`) se encarga de generar y validar los tokens JWT con tres métodos: `generateToken(User)`, que crea el token con el nombre de usuario, la fecha de emisión y la de caducidad y lo firma; `extractUsername(token)`, que verifica firma y caducidad y devuelve el usuario; y `validateToken(token)`, que envuelve a la anterior y devuelve `true` o `false` en lugar de propagar la excepción, para que el filtro de seguridad pueda usarlo directamente.
- **Decisión:** la clave se decodifica de Base64 (formato que genera `openssl`) y JJWT elige el algoritmo según su tamaño; exige un mínimo de 256 bits.
- **Problema:** el archivo se creó por error en una carpeta `security/` en la raíz del proyecto, en lugar de en `src/main/java/com/facturacion/api/security/`. Maven solo compila `src/main/java`, así que el arranque de la API terminó sin errores aunque `JwtService` no existía para Spring: un arranque correcto no demostraba nada. Además, el código tenía errores (dos métodos `validateToken` y `Jwt.parser()` en lugar de `Jwts.parser()`) que el compilador habría detectado.
- **Solución:** mover el archivo a su ubicación correcta, corregir el código y borrar la carpeta sobrante.
- **Lección:** una prueba solo vale si realmente ejercita lo que se quiere probar. En adelante, al comprobar un componente nuevo se verificará también que aparece en el log o en los resultados (por ejemplo, con una prueba unitaria), no solo que la aplicación arranque.
- **Comprobación:** tras mover y corregir el archivo, `target/classes/com/facturacion/api/security/JwtService.class` aparece compilado y la API arranca con `JwtService` registrado como componente de Spring, lo que confirma que la variable de entorno `JWT_SECRET` se resuelve correctamente y que la clave es válida para JJWT.


## 23 de octubre de 2026 (tarde) - Pruebas unitarias de JwtService

- **Decisión:** se prueba `JwtService` con pruebas unitarias de JUnit 5 sin arrancar Spring, construyendo el servicio directamente con claves propias del test (no son secretas). Así las pruebas son rápidas y no dependen de la variable de entorno `JWT_SECRET`.
- **Pruebas:** token válido (se acepta y devuelve el usuario), token manipulado (se rechaza al alterar un carácter de la firma), token caducado (se genera con duración negativa para no esperar una hora), token firmado con otra clave y texto que no es un token. Estas cuatro últimas comprueban que el servicio rechaza lo que debe rechazar, que es lo que da valor a la autenticación con JWT.
- **Problema:** `./mvnw test -Dtest=JwtServiceTest` pasó las 5 pruebas pero terminó en `BUILD FAILURE` por un segundo `JwtServiceTest` en el paquete `com.facturacion.api` que fallaba con `NoClassDefFoundError: JwtService`.
- **Causa:** el archivo de la prueba se había creado en `src/test/java/com/facturacion/api/` en lugar de en su subcarpeta `security/`, y VS Code había compilado una versión antigua sin la línea `package`, que quedó en `target/test-classes`. Es la segunda vez que un archivo acaba en una carpeta que no corresponde a su paquete.
- **Solución:** mover el archivo a `src/test/java/com/facturacion/api/security/` y ejecutar `./mvnw clean test`, que borra `target/` y elimina el `.class` obsoleto. **Lección:** la carpeta debe coincidir siempre con la línea `package`, y ante resultados raros conviene ejecutar `./mvnw clean test`, que borra `target/` y evita arrastrar clases antiguas.
- **Comprobación:** `./mvnw clean test -Dtest=JwtServiceTest` ejecuta las 5 pruebas sin fallos y termina con `BUILD SUCCESS`. Queda verificado que `JwtService` genera tokens válidos y rechaza los manipulados, caducados, firmados con otra clave y los que no son tokens.


## 23 de octubre de 2026 (tarde) - DTO de registro

- **Decisión:** `RegisterRequest` se implementa como `record` de Java (clase inmutable que genera sola constructor, accesores y `equals`), adecuado para un objeto de transferencia de datos. Contiene solo cinco campos (`username`, `password`, `companyName`, `taxId`, `address`): el cliente no puede enviar ni fijar campos internos como el identificador, que es la razón por la que se usan DTOs en lugar de exponer la entidad `User` (apartado 5.1.3 de la memoria).
- **Decisión:** las validaciones se declaran junto a cada dato (`@NotBlank`, `@Size`, `@Pattern`), con validación declarativa en el servidor (RNF-04). Aún no se ejecutan: se activarán cuando el controlador reciba la petición con `@Valid`.
- **Decisión:** la contraseña se limita a un máximo de 72 caracteres porque BCrypt ignora todo lo que supere los 72 bytes, de modo que dos contraseñas largas que solo se diferenciaran al final producirían el mismo hash.
- **Decisión:** el NIF/CIF se valida solo por formato (DNI, NIE o CIF) mediante expresión regular, sin comprobar la letra o dígito de control. Es suficiente para el prototipo; la validación completa queda como posible mejora futura.
- **Comprobación:** `./mvnw clean compile` compila 9 archivos fuente y termina con `BUILD SUCCESS`.


## 26 de octubre de 2026 - DTO de login

- **Decisión:** `LoginRequest` solo valida que `username` y `password` no estén vacíos (`@NotBlank`), sin las reglas de longitud o formato del registro. En el registro se validan los datos porque se están creando; en el login la comprobación real es la comparación con el hash almacenado. Aplicar aquí reglas de longitud haría que un mensaje de error como "contraseña demasiado corta" revelara cómo son las contraseñas válidas; con solo `@NotBlank`, cualquier intento fallido recibe el mismo mensaje genérico, en línea con el RNF-04 (los errores de inicio de sesión no revelan si el usuario existe).
- **Comprobación:** `LoginRequest.class` aparece compilado en `target/classes/com/facturacion/api/dto/`.

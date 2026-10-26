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


## 26 de octubre de 2026 (mañana) - DTO de respuesta de autenticación

- **Decisión:** `AuthResponse` (`token`, `username`) es el DTO de salida del registro y del login. Devuelve el token JWT, que el cliente guardará en memoria y enviará en la cabecera `Authorization`, y el nombre de usuario para mostrarlo en la interfaz sin otra petición. No incluye ni la contraseña, ni su hash, ni el identificador interno: es la ventaja de un DTO de salida frente a devolver la entidad `User`.
- **Decisión:** no lleva anotaciones de validación, porque estas se aplican a los datos de entrada, en los que se debe desconfiar; la respuesta la construye el propio servidor.
- **Comprobación:** `AuthResponse.class` aparece compilado en `target/classes/com/facturacion/api/dto/`.


## 26 de octubre de 2026 (mañana) - Cifrado de contraseñas

- **Decisión:** `SecurityConfig` (paquete `security`) declara un bean `PasswordEncoder` basado en `BCryptPasswordEncoder`. Spring lo inyectará en `AuthService` a través de la interfaz `PasswordEncoder`, de modo que el servicio no depende del algoritmo concreto y cambiarlo solo exigiría tocar esta clase.
- **Decisión:** se elige BCrypt porque guarda un hash con sal aleatoria incluida (dos usuarios con la misma contraseña obtienen hashes distintos) y es deliberadamente lento, lo que frena los ataques de fuerza bruta (RNF de seguridad).
- **Observación:** `SecurityConfig` empieza solo con el cifrado de contraseñas. Más adelante recibirá las reglas de qué rutas son públicas y el filtro JWT; mientras tanto Spring Security sigue con su configuración por defecto.
- **Comprobación:** `SecurityConfig.class` aparece compilado en `target/classes/com/facturacion/api/security/`.


## 26 de octubre de 2026 (mañana) - Excepciones de autenticación

- **Decisión:** se crean dos excepciones propias, `UsernameAlreadyExistsException` e `InvalidCredentialsException`, ambas `RuntimeException` para no obligar a declarar `throws` en cada método. Cada fallo tiene un significado de negocio distinto, y el servicio solo dice qué ha ocurrido sin saber nada de HTTP; más adelante `GlobalExceptionHandler` las traducirá a los códigos 409 (conflicto) y 401 (no autenticado).
- **Decisión:** el mensaje de `InvalidCredentialsException` es fijo y genérico ("Invalid username or password"), sin distinguir si falló el usuario o la contraseña. Si se indicara que el usuario no existe, un atacante podría descubrir qué usuarios están registrados (RNF-04).
- **Comprobación:** ambas clases aparecen compiladas en `target/classes/com/facturacion/api/exception/`.


## 26 de octubre de 2026 (mañana) - AuthService

- **Decisión:** `AuthService` recibe por constructor `UserRepository`, `PasswordEncoder` y `JwtService`, que Spring inyecta. Depende de la interfaz `PasswordEncoder`, no de BCrypt, lo que permite cambiar de algoritmo sin tocar el servicio.
- **Decisión:** `register` comprueba que el nombre de usuario no exista, cifra la contraseña **antes** de construir la entidad `User` (el texto en claro nunca llega a la entidad ni a la base de datos) y la guarda. No devuelve token: tras registrarse, el usuario inicia sesión, como describe el apartado 5.1.2 de la memoria. Se ejecuta en una transacción para que sea todo o nada.
- **Decisión:** `login` busca al usuario y compara la contraseña con `passwordEncoder.matches`, que lee el hash y su sal. Si el usuario no existe o la contraseña no coincide, lanza la misma `InvalidCredentialsException` con el mismo mensaje, de forma que no se revela cuál de las dos falló (RNF-04). Es una transacción de solo lectura (`readOnly = true`).
- **Observación:** `existsByUsername` es la comprobación que da un mensaje claro, pero la garantía real frente a dos registros simultáneos con el mismo nombre es la restricción `unique` de la columna `username` en la base de datos.
- **Comprobación:** `AuthService.class` aparece compilado en `target/classes/com/facturacion/api/service/`. Todavía no se ha ejercitado el servicio: las pruebas unitarias son el siguiente paso.


## 26 de octubre de 2026 (mañana) - Pruebas unitarias de AuthService

- **Decisión:** `AuthService` se prueba con JUnit 5 y Mockito, sustituyendo `UserRepository` por un doble (`@Mock`) para no necesitar base de datos. El cifrado BCrypt y el `JwtService` son los reales, de modo que se comprueba de verdad que la contraseña se guarda cifrada y que el token generado es válido. Las claves del test no son secretas.
- **Pruebas:** registro correcto (la contraseña guardada no es el texto original pero BCrypt reconoce el original contra ese hash), registro con usuario repetido (lanza la excepción y no llega a guardar nada), login correcto (devuelve un token válido que contiene el usuario), login con contraseña errónea, login con usuario inexistente y comprobación de que ambos fallos producen exactamente el mismo mensaje (RNF-04).
- **Problema:** `./mvnw clean test` terminó en `BUILD FAILURE` con 56 errores de compilación (`package org.junit.jupiter.api does not exist`, `package org.mockito does not exist`) que parecían un problema de dependencias.
- **Causa:** el archivo `AuthServiceTest.java` se había creado en `src/main/java/` en lugar de `src/test/java/`. El código de `main` no tiene JUnit ni Mockito en su classpath, y además un archivo de pruebas en `main` rompe la compilación de toda la aplicación. Es la tercera vez que un archivo acaba en una ubicación que no corresponde (antes `security/` en la raíz y `JwtServiceTest` fuera de su subcarpeta).
- **Solución:** mover el archivo a `src/test/java/com/facturacion/api/service/`. **Lección:** antes de crear cada archivo hay que comprobar que la ruta empieza por `src/main/java` (código de la aplicación) o `src/test/java` (pruebas), y que las carpetas siguientes coinciden con la línea `package`.
- **Comprobación:** `./mvnw clean test -Dtest=AuthServiceTest` ejecuta las 6 pruebas sin fallos ni errores (informe de Surefire: `Tests run: 6, Failures: 0, Errors: 0`). Queda verificado que el registro guarda la contraseña cifrada, que rechaza usuarios repetidos sin guardar nada, que el login devuelve un token válido y que los dos tipos de fallo de login son indistinguibles. También confirma que Mockito viene incluido con las dependencias de test de Spring Boot.


## 26 de octubre de 2026 (mañana) - AuthController

- **Decisión:** `AuthController` expone los dos endpoints públicos del apartado 5.1.2 de la memoria, `POST /api/auth/register` y `POST /api/auth/login`. No contiene lógica: recibe la petición, delega en `AuthService` y devuelve el resultado, manteniendo la separación en capas.
- **Decisión:** los parámetros se anotan con `@Valid`, que es lo que activa por fin las validaciones declaradas en los DTOs (`@NotBlank`, `@Size`, `@Pattern`): si un campo no las cumple, Spring rechaza la petición antes de que llegue al servicio. El registro responde `201 Created` con `@ResponseStatus`, el código adecuado cuando se crea un recurso.
- **Observación:** mientras no se configure `SecurityConfig`, Spring Security aplica su configuración por defecto, que exige autenticación en todas las rutas, por lo que estos endpoints responderían 401. Se resolverá al declarar las rutas públicas y el filtro JWT.
- **Comprobación:** `AuthController.class` aparece compilado en `target/classes/com/facturacion/api/controller/`.


## 26 de octubre de 2026 (mañana) - GlobalExceptionHandler

- **Decisión:** `GlobalExceptionHandler` (`@RestControllerAdvice`) intercepta las excepciones lanzadas por cualquier controlador y las convierte en respuestas HTTP, de modo que controladores y servicios no necesitan `try/catch`. Traduce `UsernameAlreadyExistsException` a 409 (Conflict), `InvalidCredentialsException` a 401 (Unauthorized) y los fallos de `@Valid` a 400 (Bad Request). Los códigos 403 y 404 se añadirán cuando existan clientes y facturas.
- **Decisión:** se usa `ProblemDetail`, el formato estándar de errores HTTP (RFC 9457) que ya incluye Spring, en lugar de una clase de error propia: devuelve un JSON uniforme con `status`, `title` y `detail` sin código adicional que mantener.
- **Decisión:** en los errores de validación se devuelve un mapa campo → mensaje (por ejemplo `taxId: Invalid NIF/CIF format`) para que la interfaz pueda marcar el campo concreto. Solo se devuelven el nombre del campo y el mensaje de la regla, nunca el valor rechazado, de modo que una contraseña inválida no se refleja en la respuesta ni acaba en un log.
- **Comprobación:** `GlobalExceptionHandler.class` aparece compilado en `target/classes/com/facturacion/api/exception/`.


## 26 de octubre de 2026 (mañana) - Reglas de acceso en SecurityConfig

- **Decisión:** `SecurityConfig` declara un `SecurityFilterChain` con las reglas de acceso: `/api/auth/**` es público (registro y login deben poder usarse sin token, porque es como se consigue uno) y cualquier otra ruta exige estar autenticado.
- **Decisión:** se desactiva CSRF, que protege a las aplicaciones que se autentican con cookies de sesión que el navegador envía automáticamente. Esta API se autentica con un token en una cabecera que el cliente adjunta explícitamente, por lo que ese ataque no aplica; mantenerlo activado bloquearía todos los `POST`.
- **Decisión:** se desactivan `httpBasic` y `formLogin` (el formulario de login por defecto de Spring Security que aparecía en `localhost:8080`), porque el único método de acceso es el JWT. La política de sesión es `STATELESS`: el servidor no guarda sesión y cada petición se autentica por sí sola con su token (apartado 5.1.3).
- **Decisión:** las peticiones sin credenciales a rutas protegidas responden 401 mediante `HttpStatusEntryPoint`. Sin ello Spring devolvería 403, que significa otra cosa (autenticado pero sin permiso).
- **Observación:** hasta crear `JwtAuthFilter`, las rutas protegidas responderán siempre 401, incluso con un token válido, porque aún nadie lee el token de la cabecera.
- **Comprobación:** `SecurityConfig.class` se ha recompilado con la nueva configuración.


## 26 de octubre de 2026 (mañana) - Primer arranque con la seguridad completa

- **Problema:** al arrancar la API por primera vez con `AuthController` y `SecurityConfig`, falló con `BeanCreationException ... JwtService ... Constructor threw exception`, y la causa final, al final de la traza, era `DecodingException: Illegal base64 character: '-'`.
- **Causa:** la variable de entorno `JWT_SECRET` se había definido con un texto de ejemplo (`tu-clave-generada`) en lugar de una clave real. Los guiones no son caracteres válidos en Base64, que es el formato en que `JwtService` espera la clave. Al abrir una terminal nueva la variable se había perdido y hubo que volver a definirla.
- **Solución:** generar una clave nueva con `openssl rand -base64 48` y exportarla en esa terminal antes de arrancar.
- **Observación:** el comportamiento es el deseado: la API se niega a arrancar con una clave inválida en lugar de funcionar con una insegura. Para el manual de instalación (Anexo I) conviene documentar que es necesario definir `JWT_SECRET` con una clave en Base64 de al menos 256 bits antes de arrancar, y que la variable solo vale para la terminal en la que se define. **Lección:** al leer una traza larga de Spring, la causa real está en el último bloque `Caused by`, no en el primer mensaje.


## 26 de octubre de 2026 (mañana) - Primera prueba real: registro

- **Prueba:** `POST /api/auth/register` con `curl` sobre la API en marcha, con los datos de un usuario de prueba (`fran`). Respuesta: `HTTP/1.1 201`, sin cuerpo.
- **Resultado:** la petición cruza la configuración de seguridad (ruta pública), supera las validaciones de `RegisterRequest`, y `AuthService` cifra la contraseña y guarda el usuario. Es la primera vez que se ejercita toda la cadena (controlador, servicio, repositorio y base de datos) con una petición HTTP real.
- **Observación:** la respuesta incluye cabeceras de seguridad que Spring Security añade por defecto: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-cache, no-store` y `X-XSS-Protection: 0`. Sirven como evidencia para el apartado de seguridad de la memoria.

- **Prueba:** `POST /api/auth/login` con las credenciales del usuario registrado. Respuesta: `HTTP/1.1 200` y un cuerpo JSON con el token JWT y el nombre de usuario (`{"token":"eyJ...","username":"fran"}`). La respuesta no incluye la contraseña, su hash ni el identificador interno, tal como se diseñó en `AuthResponse`.
- **Resultado:** queda demostrado de extremo a extremo el flujo de autenticación: registro, login y emisión de token.


## 26 de octubre de 2026 (mañana) - Pruebas reales de los errores de autenticación

- **Pruebas:** tres peticiones con `curl` contra la API en marcha. (1) Login con contraseña incorrecta: `401` con `"Invalid username or password"`. (2) Registro de un usuario ya existente: `409` con `"Username already in use"`. (3) Registro con datos inválidos (contraseña de 3 caracteres y NIF `XX`): `400` con `"Validation failed"` y un objeto `errors` que nombra los campos `taxId` y `password`.
- **Resultado:** `GlobalExceptionHandler` traduce cada caso al código HTTP previsto, con el formato estándar `application/problem+json`. El mensaje del 401 es el mismo se equivoque el usuario o la contraseña (RNF-04) y en el 400 no se refleja el valor rechazado: ni la contraseña ni el NIF aparecen en la respuesta.
- **Observación:** los mensajes de validación salen en idiomas distintos: `taxId` en inglés (escrito explícitamente en la anotación) y `password` en español (mensaje por defecto de Hibernate Validator, que usa el idioma del sistema donde se ejecuta la API). Se resuelve fijando el idioma de la aplicación para que la respuesta no dependa de la máquina.
- **Solución:** se fija el idioma con `spring.web.locale=en` y `spring.web.locale-resolver=fixed`, de modo que la API genera siempre los mensajes en inglés, con independencia de la configuración regional del equipo o de la cabecera `Accept-Language` del cliente. Así el comportamiento es determinista en cualquier despliegue.
- **Comprobación:** repetida la petición de datos inválidos, ambos mensajes salen en inglés: `"taxId":"Invalid NIF/CIF format"` y `"password":"size must be between 8 and 72"`.


## 26 de octubre de 2026 (mañana) - JwtAuthFilter

- **Decisión:** `JwtAuthFilter` extiende `OncePerRequestFilter` (se ejecuta una sola vez por petición) y lee la cabecera `Authorization: Bearer <token>`, la convención estándar para enviar un token, que usará el cliente JavaFX. Si el token es válido, registra en el `SecurityContext` que la petición pertenece a ese usuario, y Spring Security la considera autenticada.
- **Decisión:** si no hay cabecera o el token no es válido, el filtro no rechaza la petición, simplemente no registra autenticación. El rechazo lo produce la regla `anyRequest().authenticated()` de `SecurityConfig` con un 401, de modo que hay un único punto que decide el rechazo.
- **Decisión:** el filtro no se anota con `@Component`: Spring Boot registraría además cualquier filtro-bean como filtro general del servidor y acabaría ejecutándose en dos sitios. Se instanciará dentro de `SecurityConfig`.
- **Decisión:** el *principal* de la autenticación es el nombre de usuario (`String`). Los servicios buscarán después la entidad `User` con `findByUsername`, lo que evita una consulta a la base de datos en cada petición. La lista de permisos es vacía, porque los roles quedaron fuera del alcance del prototipo (apartado 4).
- **Comprobación:** `JwtAuthFilter.class` aparece compilado en `target/classes/com/facturacion/api/security/`.


## 26 de octubre de 2026 (mañana) - Conexión del filtro JWT en SecurityConfig

- **Decisión:** `SecurityConfig` instancia `JwtAuthFilter` a mano (`new JwtAuthFilter(jwtService)`) y lo coloca con `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`, antes del filtro que gestionaba el login con usuario y contraseña, de modo que cuando la petición llega a la regla de autorización el token ya se ha leído. `JwtService` se recibe como parámetro del método que declara la cadena de filtros.
- **Comprobación:** `SecurityConfig.class` se ha recompilado con el filtro conectado. Falta la prueba real con una ruta protegida.


## 26 de octubre de 2026 (mañana) - Prueba del filtro JWT y ruta /error

- **Prueba:** peticiones a la ruta protegida `/api/clientes`, que aún no existe: sin token (401), con un token falso (401) y con un token válido. En la tercera se esperaba 404, porque la petición debía superar la seguridad y fallar solo por no existir la ruta; se obtuvo 401. Se comprobó que el token no estaba vacío (150 caracteres), lo que descartó un fallo al extraerlo.
- **Causa:** cuando una petición termina en error, Spring la reenvía internamente a `/error`, que también pasa por las reglas de seguridad y exigía autenticación. `JwtAuthFilter` no se vuelve a ejecutar en ese reenvío, así que la petición llegaba sin identidad y se devolvía 401 en lugar del 404 (o del 400 de un JSON mal formado) que correspondía.
- **Solución:** `.requestMatchers("/api/auth/**", "/error").permitAll()`. No expone nada sensible: una petición sin token a una ruta protegida se rechaza igualmente en la primera comprobación, antes de que exista un error que reenviar.
- **Comprobación:** tras el cambio, con token válido la respuesta es `404` (la petición supera la seguridad y solo falla por no existir la ruta) y sin token sigue siendo `401`. Queda demostrado que el filtro identifica al usuario y que la nueva regla no abre rutas protegidas.
- **Lección:** una prueba con resultado inesperado no se da por buena; primero se descarta lo más simple (variable vacía) y después se busca la causa real.


## 26 de octubre de 2026 (mañana) - DTO de entrada de clientes

- **Decisión:** `ClientRequest` contiene los mismos campos editables que la entidad `Client` (`name`, `taxId`, `address`, `email`, `phone`) pero sin `id` ni `user`. El cliente de la API nunca decide a quién pertenece un cliente: el propietario se deduce del token, no del JSON, lo que impide crear o modificar clientes a nombre de otro usuario.
- **Decisión:** `name` y `taxId` son obligatorios (`@NotBlank`), coincidiendo con las columnas `not null` de la tabla, y el NIF/CIF se valida por formato. El resto de campos son opcionales; `@Email` comprueba el formato del correo solo cuando se envía.
- **Decisión:** los límites de `@Size` (150, 255, 100 y 20) coinciden con la longitud de las columnas de `Client`. Si no coincidieran, un valor demasiado largo superaría la validación y fallaría después al guardar, con un error de base de datos en lugar de un 400 limpio.
- **Observación:** la expresión regular del NIF/CIF está repetida en `RegisterRequest` y `ClientRequest`. Se deja así por ahora; si molesta más adelante, se extraerá a una constante compartida.
- **Comprobación:** `ClientRequest.class` aparece compilado en `target/classes/com/facturacion/api/dto/`.


## 26 de octubre de 2026 (mañana) - DTO de salida de clientes

- **Decisión:** `ClientResponse` es el DTO de salida de los clientes. Incluye el `id`, que el cliente JavaFX necesita para editar o borrar, pero no el propietario (`user`).
- **Decisión:** no se devuelve la entidad `Client` directamente: contiene la relación con `User`, cuyo objeto incluye la contraseña cifrada, de modo que serializarla filtraría datos del propietario. Además, al ser una relación perezosa (`LAZY`), podría provocar un error al convertirla a JSON fuera de la transacción. El DTO elige exactamente qué sale (apartado 5.1.3).
- **Decisión:** la conversión de entidad a DTO vive en un único sitio, el método estático `ClientResponse.from(Client)`, que usará el servicio.
- **Comprobación:** `ClientResponse.class` aparece compilado en `target/classes/com/facturacion/api/dto/`.


## 26 de octubre de 2026 (mañana) - Excepción de cliente no encontrado

- **Decisión:** se crea `ClientNotFoundException` y `GlobalExceptionHandler` la traduce a `404 Not Found`.
- **Decisión:** un cliente que existe pero pertenece a otro usuario también produce este 404, no un 403. Como `ClientRepository.findByIdAndUser` busca por identificador y propietario a la vez, para el servicio ese cliente simplemente no existe. Así un usuario no puede descubrir qué identificadores pertenecen a otros: un 403 le confirmaría que existe. Es la misma lógica de no revelar información que se aplica en el login (RNF-04). Los casos de uso del apartado 5.2.3 hablan de un error 404 o 403 para recursos ajenos; se elige el 404 por esta razón.
- **Comprobación:** `ClientNotFoundException.class` y `GlobalExceptionHandler.class` aparecen compilados en `target/classes/com/facturacion/api/exception/`.


## 26 de octubre de 2026 (mañana) - ClientService

- **Decisión:** todos los métodos de `ClientService` reciben el nombre de usuario extraído del token, nunca un usuario ni un identificador de propietario enviado por el cliente de la API. Es la pieza que garantiza el aislamiento de datos entre usuarios.
- **Decisión:** consultar, editar y borrar pasan por un único método privado, `findOwnedClient`, que busca por identificador y propietario y lanza `ClientNotFoundException` si no lo encuentra. Al estar centralizada la comprobación, no se puede olvidar en uno de los métodos.
- **Decisión:** `update` no llama a `save`: el método es transaccional y la entidad está gestionada por Hibernate, que detecta los cambios hechos con los setters y los guarda al terminar la transacción.
- **Decisión:** el listado admite un texto de búsqueda opcional que filtra por nombre sin distinguir mayúsculas (buscador de la tabla, apartado 5.2.1); si no llega texto, devuelve todos los clientes del usuario.
- **Decisión de alcance:** la regla de que no se puede dar de baja un cliente con facturas emitidas (CU-03) se aplaza hasta la tarea T14, cuando existan el repositorio y el servicio de facturas. Es una tarea de prioridad media, de las que el apartado 6.4 prevé recortar primero si hay desvíos. **Pendiente:** implementar esta regla en T14 y probarla.
- **Comprobación:** `ClientService.class` aparece compilado en `target/classes/com/facturacion/api/service/`.


## 26 de octubre de 2026 (mediodía) - ClientController

- **Decisión:** `ClientController` expone los cinco endpoints de gestión de clientes del apartado 5.1.2: listar (con `?search=` opcional), consultar, crear, modificar y borrar, bajo `/api/clients`. Crear responde `201` con el cliente creado (incluye el identificador asignado) y borrar responde `204` sin cuerpo.
- **Decisión:** el controlador obtiene el usuario de `Principal`, que Spring rellena con la identidad que `JwtAuthFilter` dejó en el `SecurityContext`; `principal.getName()` devuelve el nombre de usuario del token. Es el enlace entre la autenticación y los datos: el propietario nunca viaja en la petición.
- **Decisión:** la ruta cuelga de `/api/clients`, por lo que cae bajo la regla `anyRequest().authenticated()` y exige token sin necesidad de modificar `SecurityConfig`.
- **Decisión de nombres:** la memoria define los endpoints como `/api/clientes` y `/api/facturas`; se usa `/api/clients` para mantener el código en inglés. **Pendiente:** actualizar los endpoints en el apartado 5.1.2 de la memoria al unificar los nombres de clases y rutas.
- **Comprobación:** `ClientController.class` aparece compilado en `target/classes/com/facturacion/api/controller/`.

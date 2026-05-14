# Firma Digital API - Documentacion de Endpoints

**Base URL:** `https://{host}/api`
**Autenticacion:** JWT Bearer Token (header `Authorization: Bearer <token>`)
**Content-Type:** `application/x-www-form-urlencoded` (salvo donde se indique)

---

## Tabla de Contenidos

1. [Autenticacion](#1-autenticacion)
2. [Firmar Documento](#2-firmar-documento)
3. [Firmar Documento con QR](#3-firmar-documento-con-qr)
4. [Verificar Documento](#4-verificar-documento)
5. [Validar Certificado Digital](#5-validar-certificado-digital)
6. [Consultar Certificado (Revocacion)](#6-consultar-certificado-revocacion)
7. [Fecha y Hora del Servidor](#7-fecha-y-hora-del-servidor)
8. [Version](#8-version)
9. [Codigos de Error](#9-codigos-de-error)

---

## 1. Autenticacion

### 1.1 Obtener Token JWT

```
POST /auth/token
```

**Content-Type:** `application/x-www-form-urlencoded`

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `subject` | String | Si | Identificador del usuario/sistema |
| `expirationMinutes` | Integer | No | Minutos de vigencia del token |

**Request:**
```
POST /auth/token
Content-Type: application/x-www-form-urlencoded

subject=sistema-externo&expirationMinutes=60
```

**Response exitosa:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzaXN0ZW1hLWV4dGVybm8iLCJpYXQiOjE3MTU...",
  "expirationDate": "2026-05-14T12:07:14.000-05:00"
}
```

### 1.2 Validar Token JWT

```
POST /auth/validate
```

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `token` | String | Si | Token JWT a validar |

**Response exitosa:**
```json
{
  "valid": true,
  "subject": "sistema-externo"
}
```

---

## 2. Firmar Documento

Firma un PDF digitalmente con certificado PKCS#12 y sellado de tiempo (TSA).

```
POST /appfirmardocumento
```

**Requiere:** `@Secured` (JWT)

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `pkcs12` | String | Si | Certificado PKCS#12 (.p12) codificado en Base64 |
| `password` | String | Si | Contrasena del certificado |
| `documento` | String | Si | Documento PDF codificado en Base64 |
| `json` | String | No | Metadatos JSON (ver estructura abajo) |

**Estructura del parametro `json` (todos los campos son opcionales):**
```json
{
  "razon": "Firma del documento",
  "localizacion": "Quito, Ecuador"
}
```

| Campo JSON | Tipo | Requerido | Descripcion |
|------------|------|-----------|-------------|
| `razon` | String | No | Razon de la firma (se muestra en metadatos del PDF) |
| `localizacion` | String | No | Ubicacion donde se firma |
| `cargo` | String | No | Cargo del firmante |
| `identificacion` | String | No | Cedula del firmante para TSA. **Se extrae automaticamente del certificado .p12** si no se proporciona. Solo enviar si se quiere forzar un valor diferente. |

> **Nota sobre Sellado de Tiempo:** La `identificacion` (cedula) se extrae automaticamente del certificado PKCS#12 usando `CertEcUtils.getDatosUsuarios()`. No es necesario enviarla manualmente. El sellado de tiempo se aplica automaticamente si la cedula se puede extraer del certificado.

**Request completo:**
```
POST /appfirmardocumento
Content-Type: application/x-www-form-urlencoded
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

pkcs12=MIIKYwIBAzCCCi0GCSqGSIb3DQEHAaCCCh4E...&password=miPassword123&documento=JVBERi0xLjQKMSAwIG9iago...&json={"razon":"Aprobacion","localizacion":"Quito","identificacion":"1234567890"}
```

**Response exitosa (200):**
```json
{
  "resultado": "OK",
  "mensaje": "Documento firmado exitosamente",
  "documentoFirmado": "JVBERi0xLjQKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZw..."
}
```

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| `resultado` | String | `"OK"` si fue exitoso |
| `mensaje` | String | Mensaje descriptivo |
| `documentoFirmado` | String | PDF firmado codificado en Base64 |

**Response error (200 con error en body):**
```json
{
  "resultado": "ERROR",
  "mensaje": "Contrasena del certificado incorrecta"
}
```

**Errores posibles:**

| Mensaje | Causa |
|---------|-------|
| `El certificado PKCS#12 es requerido` | Parametro `pkcs12` vacio |
| `La contrasena del certificado es requerida` | Parametro `password` vacio |
| `El documento a firmar es requerido` | Parametro `documento` vacio |
| `Contrasena del certificado incorrecta` | Password no corresponde al .p12 |
| `Error de autenticacion TSA (401): Credenciales incorrectas.` | Cedula rechazada por servidor TSA |
| `Error de conflicto TSA (409): Hash duplicado.` | Hash ya sellado en TSA |
| `Error de formato: ...` | Base64 invalido |

---

## 3. Firmar Documento con QR

Firma un PDF con estampado visual QR + sellado de tiempo (TSA).

```
POST /appfirmardocumentoconqr
```

**Requiere:** `@Secured` (JWT)

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `pkcs12` | String | Si | Certificado PKCS#12 codificado en Base64 |
| `password` | String | Si | Contrasena del certificado |
| `documento` | String | Si | Documento PDF codificado en Base64 |
| `json` | String | No | Metadatos JSON (ver estructura abajo) |

**Estructura del parametro `json`:**
```json
{
  "razon": "Aprobacion del contrato",
  "localizacion": "Quito, Ecuador",
  "infoQR": "https://verificar.ejemplo.com/doc/ABC123",
  "qrPagina": 1,
  "qrPosX": 50,
  "qrPosY": 50,
  "qrAncho": 200,
  "qrAlto": 100
}
```

| Campo JSON | Tipo | Requerido | Descripcion |
|------------|------|-----------|-------------|
| `razon` | String | No | Razon de la firma |
| `localizacion` | String | No | Ubicacion de la firma |
| `cargo` | String | No | Cargo del firmante |
| `identificacion` | String | No | Se extrae automaticamente del .p12 (ver nota en endpoint de firma) |
| `infoQR` | String | No | Texto/URL adicional codificado en el QR |
| `qrPagina` | Integer | No | Pagina donde colocar el QR (default: ultima) |
| `qrPosX` | Float | No | Posicion X inferior izquierda en puntos (default: 50) |
| `qrPosY` | Float | No | Posicion Y inferior izquierda en puntos (default: 50) |
| `qrAncho` | Float | No | Posicion X superior derecha en puntos (default: 200) |
| `qrAlto` | Float | No | Posicion Y superior derecha en puntos (default: 100) |

**Request completo:**
```
POST /appfirmardocumentoconqr
Content-Type: application/x-www-form-urlencoded
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

pkcs12=MIIKYwIBAzCC...&password=miPassword123&documento=JVBERi0xLjQK...&json={"razon":"Aprobacion","localizacion":"Quito","identificacion":"1234567890","infoQR":"https://verificar.ejemplo.com","qrPosX":400,"qrPosY":50,"qrAncho":560,"qrAlto":120}
```

**Response exitosa (200):**
```json
{
  "resultado": "OK",
  "mensaje": "Documento firmado con QR exitosamente",
  "documentoFirmado": "JVBERi0xLjQKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZw..."
}
```

**Response error:** Misma estructura que endpoint de firma simple.

---

## 4. Verificar Documento

Verifica todas las firmas de un PDF incluyendo sellado de tiempo (TSA), integridad, y validacion de certificados.

```
POST /appverificardocumento
```

**Requiere:** `@Secured` (JWT)

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `documento` | String | Si | Documento PDF firmado codificado en Base64 |

**Request:**
```
POST /appverificardocumento
Content-Type: application/x-www-form-urlencoded
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

documento=JVBERi0xLjQKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZw...
```

### Response exitosa - Documento CON sellado de tiempo (200):

```json
{
  "resultado": "OK",
  "signValidate": true,
  "docValidate": true,
  "firmaValida": true,
  "numeroFirmas": 1,
  "firmas": [
    {
      "numeroFirma": 1,
      "issuedTo": "JUAN ANDRES PEREZ GARCIA",
      "issuedBy": "LAZZATE CIA. LTDA.",
      "validFrom": "2025-08-18T14:38:07.000-05:00",
      "validTo": "2026-08-18T14:38:07.000-05:00",
      "fechaFirma": "2026-05-14T11:07:14.000-05:00",
      "certificadoValido": true,
      "signVerify": true,
      "docReason": "Aprobacion del contrato",
      "docLocation": "Quito, Ecuador",
      "keyUsages": "Firma Electronica, No Repudio, ",
      "docValidTimeStamp": true,
      "docTimeStamp": "2026-05-14T11:07:15.123-05:00",
      "docTimeStampIssuedBy": "UANATACA S.A.",
      "selladoTiempoFecha": "2026-05-14T11:07:15.123-05:00",
      "selladoTiempoEmitidoPor": "UANATACA S.A.",
      "selladoTiempoValido": true,
      "cnTimeStamp": "MINTEL - TSU02",
      "datosUsuario": {
        "cedula": "1234567890",
        "nombre": "JUAN ANDRES",
        "apellido": "PEREZ GARCIA",
        "institucion": "EMPRESA XYZ S.A.",
        "cargo": "GERENTE",
        "certificadoDigitalValido": true
      }
    }
  ]
}
```

### Response exitosa - Documento SIN sellado de tiempo (200):

```json
{
  "resultado": "OK",
  "signValidate": true,
  "docValidate": true,
  "firmaValida": true,
  "numeroFirmas": 1,
  "firmas": [
    {
      "numeroFirma": 1,
      "issuedTo": "JUAN ANDRES PEREZ GARCIA",
      "issuedBy": "SECURITY DATA S.A.",
      "validFrom": "2025-01-15T10:00:00.000-05:00",
      "validTo": "2027-01-15T10:00:00.000-05:00",
      "fechaFirma": "2026-05-14T09:30:00.000-05:00",
      "certificadoValido": true,
      "signVerify": true,
      "docReason": "Firma del documento",
      "docLocation": "Ecuador",
      "keyUsages": "Firma Electronica, No Repudio, ",
      "docValidTimeStamp": false,
      "datosUsuario": {
        "cedula": "1234567890",
        "nombre": "JUAN ANDRES",
        "apellido": "PEREZ GARCIA",
        "institucion": "EMPRESA XYZ S.A.",
        "cargo": null,
        "certificadoDigitalValido": true
      }
    }
  ]
}
```

### Response - Documento con multiples firmas (200):

```json
{
  "resultado": "OK",
  "signValidate": true,
  "docValidate": true,
  "firmaValida": true,
  "numeroFirmas": 2,
  "firmas": [
    {
      "numeroFirma": 1,
      "issuedTo": "MARIA LOPEZ",
      "issuedBy": "LAZZATE CIA. LTDA.",
      "fechaFirma": "2026-05-14T09:00:00.000-05:00",
      "certificadoValido": true,
      "signVerify": true,
      "docValidTimeStamp": true,
      "docTimeStamp": "2026-05-14T09:00:01.000-05:00",
      "docTimeStampIssuedBy": "UANATACA S.A.",
      "selladoTiempoFecha": "2026-05-14T09:00:01.000-05:00",
      "selladoTiempoEmitidoPor": "UANATACA S.A.",
      "selladoTiempoValido": true,
      "cnTimeStamp": "MINTEL - TSU02",
      "datosUsuario": { "cedula": "0912345678", "nombre": "MARIA", "apellido": "LOPEZ", "certificadoDigitalValido": true }
    },
    {
      "numeroFirma": 2,
      "issuedTo": "PEDRO MARTINEZ",
      "issuedBy": "SECURITY DATA S.A.",
      "fechaFirma": "2026-05-14T10:30:00.000-05:00",
      "certificadoValido": true,
      "signVerify": true,
      "docValidTimeStamp": true,
      "docTimeStamp": "2026-05-14T10:30:01.000-05:00",
      "docTimeStampIssuedBy": "UANATACA S.A.",
      "selladoTiempoFecha": "2026-05-14T10:30:01.000-05:00",
      "selladoTiempoEmitidoPor": "UANATACA S.A.",
      "selladoTiempoValido": true,
      "cnTimeStamp": "MINTEL - TSU02",
      "datosUsuario": { "cedula": "1712345678", "nombre": "PEDRO", "apellido": "MARTINEZ", "certificadoDigitalValido": true }
    }
  ]
}
```

### Response - Documento sin firmas (200):

```json
{
  "resultado": "OK",
  "firmaValida": false,
  "numeroFirmas": 0,
  "mensaje": "El documento no contiene firmas digitales"
}
```

### Response - Certificado revocado (200):

```json
{
  "resultado": "OK",
  "signValidate": false,
  "docValidate": true,
  "firmaValida": false,
  "numeroFirmas": 1,
  "firmas": [
    {
      "numeroFirma": 1,
      "issuedTo": "JUAN PEREZ",
      "issuedBy": "LAZZATE CIA. LTDA.",
      "fechaFirma": "2026-05-14T11:00:00.000-05:00",
      "revocated": "2026-04-01T00:00:00.000-05:00",
      "certificadoValido": false,
      "signVerify": true,
      "docValidTimeStamp": false,
      "datosUsuario": { "cedula": "1234567890", "nombre": "JUAN", "apellido": "PEREZ", "certificadoDigitalValido": false }
    }
  ]
}
```

### Descripcion de campos de respuesta de verificacion:

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| `resultado` | String | `"OK"` o `"ERROR"` |
| `signValidate` | Boolean | `true` si TODAS las firmas son validas |
| `docValidate` | Boolean | `true` si el documento no fue modificado despues de firmar |
| `firmaValida` | Boolean | Alias de `signValidate` |
| `numeroFirmas` | Integer | Cantidad de firmas en el documento |
| `error` | String | Mensaje de error (solo si aplica) |

**Campos por firma (`firmas[]`):**

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| `numeroFirma` | Integer | Indice de la firma (1-based) |
| `issuedTo` | String | Nombre del firmante (CN del certificado) |
| `issuedBy` | String | Entidad certificadora que emitio el certificado |
| `validFrom` | String | Fecha de inicio de validez del certificado (ISO-8601) |
| `validTo` | String | Fecha de fin de validez del certificado (ISO-8601) |
| `fechaFirma` | String | Fecha y hora en que se genero la firma (ISO-8601) |
| `revocated` | String | Fecha de revocacion del certificado (ISO-8601, `null` si no revocado) |
| `certificadoValido` | Boolean | `true` si el certificado era valido al momento de firmar |
| `signVerify` | Boolean | `true` si la integridad criptografica de la firma es valida |
| `docReason` | String | Razon de la firma (metadato del PDF) |
| `docLocation` | String | Ubicacion de la firma (metadato del PDF) |
| `keyUsages` | String | Usos de la llave del certificado |

**Campos de Sellado de Tiempo (TSA):**

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| `docValidTimeStamp` | Boolean | `true` si la firma tiene sello de tiempo valido, `false` si no tiene |
| `docTimeStamp` | String | Fecha/hora del sello de tiempo emitido por TSA (ISO-8601). Solo presente si tiene TSA |
| `docTimeStampIssuedBy` | String | CA que emitio el certificado TSA (ej: `"UANATACA S.A."`) |
| `selladoTiempoFecha` | String | Alias de `docTimeStamp` (formato alternativo) |
| `selladoTiempoEmitidoPor` | String | Alias de `docTimeStampIssuedBy` |
| `selladoTiempoValido` | Boolean | Alias de `docValidTimeStamp` |
| `cnTimeStamp` | String | Common Name del certificado TSA (ej: `"MINTEL - TSU02"`) |

**Datos del usuario (`datosUsuario`):**

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| `cedula` | String | Numero de cedula del firmante |
| `nombre` | String | Nombre(s) del firmante |
| `apellido` | String | Apellido(s) del firmante |
| `institucion` | String | Institucion/empresa del firmante |
| `cargo` | String | Cargo del firmante |
| `certificadoDigitalValido` | Boolean | `true` si el certificado es reconocido por una CA ecuatoriana valida |

**Response error:**
```json
{
  "resultado": "ERROR",
  "mensaje": "El documento no es un PDF valido o no contiene firmas reconocibles",
  "firmaValida": false
}
```

---

## 5. Validar Certificado Digital

Valida un certificado PKCS#12 sin firmar documento (vigencia, revocacion, datos).

```
POST /appvalidarcertificadodigital
```

**Requiere:** `@Secured` (JWT)

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `pkcs12` | String | Si | Certificado PKCS#12 codificado en Base64 |
| `password` | String | Si | Contrasena del certificado |

**Request:**
```
POST /appvalidarcertificadodigital
Content-Type: application/x-www-form-urlencoded
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

pkcs12=MIIKYwIBAzCC...&password=miPassword123
```

**Response exitosa (200):**
```json
{
  "resultado": "OK",
  "certificadoValido": true,
  "revocado": false,
  "diasParaExpirar": 365,
  "serial": "123456789",
  "subject": "CN=JUAN PEREZ, ...",
  "issuer": "CN=LAZZATE CIA. LTDA., ...",
  "validoDesde": "2025-08-18T14:38:07.000-05:00",
  "validoHasta": "2026-08-18T14:38:07.000-05:00",
  "datosUsuario": {
    "cedula": "1234567890",
    "nombre": "JUAN",
    "apellido": "PEREZ",
    "institucion": "EMPRESA XYZ",
    "cargo": "GERENTE",
    "certificadoDigitalValido": true
  }
}
```

---

## 6. Consultar Certificado (Revocacion)

### 6.1 Verificar si esta revocado

```
GET /certificado/revocado/{serial}
```

| Parametro | Tipo | Ubicacion | Descripcion |
|-----------|------|-----------|-------------|
| `serial` | String | Path | Numero de serie del certificado |

**Response:** `true` o `false` (text/plain)

### 6.2 Obtener fecha de revocacion

```
GET /certificado/fechaRevocado/{serial}
```

**Response:** Fecha de revocacion o vacio si no esta revocado (text/plain)

---

## 7. Fecha y Hora del Servidor

### 7.1 POST (text/plain)

```
POST /fecha-hora
```

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `base64` | String | No | Parametro opcional |

**Response:** `2026-05-14T11:07:14-05:00` (text/plain ISO-8601)

### 7.2 GET (JSON)

```
GET /fecha-hora
```

**Response:**
```json
{
  "fecha": "2026-05-14T11:07:14.000-05:00",
  "timestamp": 1778810834000,
  "timezone": "America/Guayaquil"
}
```

---

## 8. Version

### 8.1 GET

```
GET /version
```

**Response:**
```json
{
  "version": "4.1.0"
}
```

### 8.2 POST

```
POST /version
```

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `base64` | String | No | Informacion del cliente codificada en Base64 |

---

## 9. Codigos de Error

Todos los endpoints retornan HTTP 200 con error en el body JSON. Excepciones:

| HTTP Code | Causa |
|-----------|-------|
| `401` | Token JWT invalido, expirado, o ausente en endpoints `@Secured` |
| `413` | Request excede tamano maximo permitido (RequestSizeFilter) |
| `200` + `resultado: "ERROR"` | Error de negocio (certificado invalido, PDF corrupto, TSA fallo, etc.) |

**Estructura estandar de error:**
```json
{
  "resultado": "ERROR",
  "mensaje": "Descripcion del error"
}
```

---

## Notas sobre Sellado de Tiempo (TSA)

- **Servidor TSA:** `https://tsa.uanatacaec.com/tsa/timestamp` (operado por UANATACA S.A.)
- **Credenciales:** Se usa la cedula del firmante como usuario y contrasena. Se extrae automaticamente del certificado .p12 via `CertEcUtils.getDatosUsuarios()`
- **Protocolo:** RFC 3161 sobre HTTP POST
- **Automatico:** Solo con subir el .p12 y el PDF, el sellado de tiempo se aplica automaticamente. No requiere enviar `identificacion` manualmente
- **Fallback:** Si la cedula no se puede extraer del certificado, la firma se genera SIN sellado de tiempo (PAdES basico)
- **Emisor del sello:** Siempre aparece UANATACA S.A. como emisor del sello, independientemente de la CA del certificado del firmante
- **CN del certificado TSA:** Tipicamente `"MINTEL - TSU02"`

### Flujo de sellado de tiempo en firma:

```
1. Se extrae cedula del certificado .p12 via CertEcUtils.getDatosUsuarios()
2. PadesBasic crea TSAClientBouncyCastle(tsaUrl, cedula, cedula)
3. iText firma el PDF y calcula hash del PKCS#7
4. TSAClient envia hash al servidor TSA via HTTP POST (RFC 3161)
5. TSA responde con TimeStampToken firmado por certificado MINTEL-TSU02
6. Token se embebe como atributo no firmado en la firma PKCS#7
7. PDF firmado retornado al cliente con sello de tiempo incluido
```

### Flujo de verificacion de sellado de tiempo:

```
1. Se extraen firmas del PDF (PadesSigner.getSigners)
2. Para cada firma, se obtiene TimeStampToken del PKCS#7
3. Se valida la firma del token contra certificado TSA
4. Se verifica que el certificado TSA tenga OID 1.3.6.1.5.5.7.3.8 (timeStamping)
5. Se valida el certificado TSA contra su CA raiz
6. Se extrae: fecha del sello, emisor (CA), CN del certificado TSA
7. Se retorna docValidTimeStamp=true si todo es valido
```

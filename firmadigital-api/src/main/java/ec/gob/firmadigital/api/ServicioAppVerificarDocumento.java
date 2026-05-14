/*
 * Firma Digital: API
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ec.gob.firmadigital.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ec.gob.firmadigital.api.security.Secured;
import ec.gob.firmadigital.libreria.certificate.to.Certificado;
import ec.gob.firmadigital.libreria.certificate.to.DatosUsuario;
import ec.gob.firmadigital.libreria.certificate.to.Documento;
import ec.gob.firmadigital.libreria.sign.SignInfo;
import ec.gob.firmadigital.libreria.sign.pdf.PadesSigner;
import ec.gob.firmadigital.libreria.utils.Utils;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.itextpdf.kernel.pdf.PdfReader;

import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST Web Service para verificar documentos firmados.
 * Usa la libreria de firma digital con verificacion completa incluyendo
 * sellado de tiempo (TSA), integridad de firma, y validacion de certificados.
 *
 * @author Christian Espinosa, Misael Fernandez
 */
@Path("/appverificardocumento")
public class ServicioAppVerificarDocumento extends RequestSizeFilter {

    private static final Logger LOGGER = Logger.getLogger(ServicioAppVerificarDocumento.class.getName());
    private static final SimpleDateFormat SDF_ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    @POST
    @Secured
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String verificarDocumento(
            @FormParam("documento") String documentoBase64
    ) {
        LOGGER.log(Level.INFO, "Iniciando verificacion de documento firmado");

        try {
            if (documentoBase64 == null || documentoBase64.isEmpty()) {
                return crearRespuestaError("El documento es requerido");
            }

            byte[] docBytes = decodificarBase64(documentoBase64);

            // Extraer firmas del PDF
            PadesSigner padesSigner = new PadesSigner();
            List<SignInfo> signInfos = padesSigner.getSigners(docBytes);

            // Verificacion completa con sellado de tiempo
            PdfReader pdfReader = new PdfReader(new ByteArrayInputStream(docBytes));
            Documento documento = Utils.pdfToDocumento(pdfReader, signInfos);

            if (documento.getCertificados() == null || documento.getCertificados().isEmpty()) {
                JsonObject response = new JsonObject();
                response.addProperty("resultado", "OK");
                response.addProperty("firmaValida", false);
                response.addProperty("numeroFirmas", 0);
                response.addProperty("mensaje", documento.getError() != null
                        ? documento.getError()
                        : "El documento no contiene firmas digitales");
                return new Gson().toJson(response);
            }

            // Construir respuesta completa
            JsonObject response = new JsonObject();
            response.addProperty("resultado", "OK");
            response.addProperty("signValidate", documento.getSignValidate());
            response.addProperty("docValidate", documento.getDocValidate());
            response.addProperty("firmaValida", documento.getSignValidate());
            response.addProperty("numeroFirmas", documento.getCertificados().size());
            if (documento.getError() != null) {
                response.addProperty("error", documento.getError());
            }

            JsonArray firmasArray = new JsonArray();
            int firmaIndex = 1;

            for (Certificado cert : documento.getCertificados()) {
                JsonObject firmaObj = new JsonObject();
                firmaObj.addProperty("numeroFirma", firmaIndex++);
                firmaObj.addProperty("issuedTo", cert.getIssuedTo());
                firmaObj.addProperty("issuedBy", cert.getIssuedBy());

                if (cert.getValidFrom() != null) {
                    firmaObj.addProperty("validFrom", SDF_ISO8601.format(cert.getValidFrom().getTime()));
                }
                if (cert.getValidTo() != null) {
                    firmaObj.addProperty("validTo", SDF_ISO8601.format(cert.getValidTo().getTime()));
                }
                if (cert.getSignGenerated() != null) {
                    firmaObj.addProperty("fechaFirma", SDF_ISO8601.format(cert.getSignGenerated().getTime()));
                }
                if (cert.getRevocated() != null) {
                    firmaObj.addProperty("revocated", SDF_ISO8601.format(cert.getRevocated().getTime()));
                }

                firmaObj.addProperty("certificadoValido", cert.getCertificateValidated());
                firmaObj.addProperty("signVerify", cert.getSignVerify());
                firmaObj.addProperty("docReason", cert.getDocReason());
                firmaObj.addProperty("docLocation", cert.getDocLocation());
                firmaObj.addProperty("keyUsages", cert.getKeyUsages());

                // Sellado de Tiempo (TSA)
                firmaObj.addProperty("docValidTimeStamp", cert.getDocValidTimeStamp());
                if (cert.getDocTimeStamp() != null) {
                    firmaObj.addProperty("docTimeStamp", SDF_ISO8601.format(cert.getDocTimeStamp()));
                    firmaObj.addProperty("docTimeStampIssuedBy", cert.getDocTimeStampIssuedBy());
                    firmaObj.addProperty("selladoTiempoFecha", SDF_ISO8601.format(cert.getDocTimeStamp()));
                    firmaObj.addProperty("selladoTiempoEmitidoPor", cert.getDocTimeStampIssuedBy());
                    firmaObj.addProperty("selladoTiempoValido", cert.getDocValidTimeStamp());
                    if (cert.getCnTimeStamp() != null) {
                        firmaObj.addProperty("cnTimeStamp", cert.getCnTimeStamp());
                    }
                }

                // Datos del usuario/firmante
                DatosUsuario datos = cert.getDatosUsuario();
                if (datos != null) {
                    JsonObject datosObj = new JsonObject();
                    datosObj.addProperty("cedula", datos.getCedula());
                    datosObj.addProperty("nombre", datos.getNombre());
                    datosObj.addProperty("apellido", datos.getApellido());
                    datosObj.addProperty("institucion", datos.getInstitucion());
                    datosObj.addProperty("cargo", datos.getCargo());
                    datosObj.addProperty("certificadoDigitalValido", datos.isCertificadoDigitalValido());
                    firmaObj.add("datosUsuario", datosObj);
                }

                firmasArray.add(firmaObj);
            }

            response.add("firmas", firmasArray);

            return new Gson().toJson(response);

        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Error de formato en los datos: {0}", e.getMessage());
            return crearRespuestaError("Error de formato: El documento no esta correctamente codificado en Base64");
        } catch (ec.gob.firmadigital.libreria.exceptions.InvalidFormatException e) {
            LOGGER.log(Level.SEVERE, "Formato de documento invalido: {0}", e.getMessage());
            return crearRespuestaError("El documento no es un PDF valido o no contiene firmas reconocibles");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al verificar documento: {0}", e);
            return crearRespuestaError("Error al verificar documento: " + e.getMessage());
        }
    }

    private String crearRespuestaError(String mensaje) {
        JsonObject error = new JsonObject();
        error.addProperty("resultado", "ERROR");
        error.addProperty("mensaje", mensaje);
        error.addProperty("firmaValida", false);
        return new Gson().toJson(error);
    }

    private byte[] decodificarBase64(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            throw new IllegalArgumentException("Cadena Base64 vacia");
        }

        String cleaned = base64String.replaceAll("\\s+", "");

        try {
            return Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException e1) {
            LOGGER.log(Level.WARNING, "Fallo decodificacion estandar, intentando con MIME decoder: {0}", e1.getMessage());

            try {
                return Base64.getMimeDecoder().decode(cleaned);
            } catch (IllegalArgumentException e2) {
                LOGGER.log(Level.SEVERE, "Error al decodificar Base64 con ambos decoders: {0}", e2.getMessage());
                throw new IllegalArgumentException("El contenido Base64 no es valido. Verifique que el contenido este correctamente codificado.");
            }
        }
    }
}

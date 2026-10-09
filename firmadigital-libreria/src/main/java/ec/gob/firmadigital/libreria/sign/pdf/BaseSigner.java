/*
 * Copyright (C) 2021 
 * Authors: Ricardo Arguello, Misael Fernández
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.*
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package ec.gob.firmadigital.libreria.sign.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.logging.Logger;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.xmp.XMPConst;
import com.itextpdf.kernel.xmp.XMPException;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.itextpdf.kernel.xmp.XMPMetaFactory;
import com.itextpdf.signatures.PdfSignatureAppearance;

import ec.gob.firmadigital.libreria.certificate.CertEcUtils;
import ec.gob.firmadigital.libreria.certificate.to.DatosUsuario;
import ec.gob.firmadigital.libreria.exceptions.EntidadCertificadoraNoValidaException;
import ec.gob.firmadigital.libreria.sign.RubricaSigner;
import ec.gob.firmadigital.libreria.sign.pdf.appearance.CustomAppearance;
import ec.gob.firmadigital.libreria.sign.pdf.appearance.Information1Appearance;
import ec.gob.firmadigital.libreria.sign.pdf.appearance.Information2Appearance;
import ec.gob.firmadigital.libreria.sign.pdf.appearance.QrAppereance;
import ec.gob.firmadigital.libreria.utils.BouncyCastleUtils;
import ec.gob.firmadigital.libreria.utils.PropertiesUtils;
import ec.gob.firmadigital.libreria.utils.Utils;
import java.util.logging.Level;

public abstract class BaseSigner {

    private static final Logger LOGGER = Logger.getLogger(BaseSigner.class.getName());

    public static final String SIGNING_REASON = "signingReason";
    public static final String SIGNING_LOCATION = "signingLocation";
    public static final String SIGN_TIME = "signTime";
    public static final String SIGNATURE_PAGE = "signingPage";
    public static final String LAST_PAGE = "page";
    public static final String FONT_SIZE = "font";
    public static final String TYPE_SIG = "typeSignature";
    public static final String INFO_QR = "infoQR";

    static {
        BouncyCastleUtils.initializeBouncyCastle();
    }

    // ETSI TS 102 778-1 V1.1.1 (2009-07)
    // PAdES Basic - Profile based on ISO 32000-1
    /**
     * @param is
     * @param signer
     * @param params
     * @param certChain
     * @return
     * @throws java.io.IOException
     */
    public byte[] sign(InputStream is, RubricaSigner signer, Certificate[] certChain, Properties params)
            throws IOException {
        byte[] pdfBytes = is.readAllBytes();
        pdfBytes = stripPdfAConformanceIfNeeded(pdfBytes);
        try (PdfReader pdfReader = new PdfReader(new ByteArrayInputStream(pdfBytes)) {
            @Override
            public boolean hasRebuiltXref() {
                return false;
            }
        }; ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            StampingProperties stampingProperties = new StampingProperties();
            //TODO Edison Lomas Almeida: La línea siguiente genera error PdfException: Append mode requires a document without errors, even if recovery is possible.
            stampingProperties.useAppendMode();
            com.itextpdf.signatures.PdfSigner pdfSigner = new com.itextpdf.signatures.PdfSigner(pdfReader, os,
                    stampingProperties);
            X509Certificate x509Certificate = (X509Certificate) certChain[0];
            Properties extraParams = params != null ? params : new Properties();
            // Motivo de la firma
            String reason = extraParams.getProperty(SIGNING_REASON);
            // Lugar de realizacion de la firma
            String location = extraParams.getProperty(SIGNING_LOCATION);
            // Fecha y hora de la firma, en formato ISO-8601
            String signTime = extraParams.getProperty(SIGN_TIME);
            // Tamaño letra
            float fontSize = 3;
            try {
                if (extraParams.getProperty(FONT_SIZE) == null) {
                    fontSize = 3;
                } else {
                    fontSize = Float.parseFloat(extraParams.getProperty(FONT_SIZE).trim());
                }
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Se ha indicado un tama\u00f1o de letra invalida (''{0}''), se usara el tama\u00f1o por defecto: {1} {2}", new Object[]{extraParams.getProperty(FONT_SIZE), fontSize, e});
            }
            // Tipo de firma (Información, QR)
            String typeSig = extraParams.getProperty(TYPE_SIG);
            if (typeSig != null && (typeSig.equals("QR") && extraParams.getProperty(FONT_SIZE) == null)) {
                fontSize = 4.5f;
            }
            String infoQR = "";
            if (extraParams.getProperty(INFO_QR) == null) {
                infoQR = "";
            } else {
                infoQR = extraParams.getProperty(INFO_QR, "").trim();
            }
            // Pagina donde situar la firma visible
            int page = 0;
            try {
                if (extraParams.getProperty(LAST_PAGE) == null) {
                    page = 0;
                } else {
                    page = Integer.parseInt(extraParams.getProperty(LAST_PAGE).trim());
                }
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Se ha indicado un numero de pagina invalido (''{0}''), se usara la ultima pagina: {1}", new Object[]{extraParams.getProperty(LAST_PAGE), e});
            }
            if (page == 0 || page < 0 || page > pdfSigner.getDocument().getNumberOfPages()) {
                page = pdfSigner.getDocument().getNumberOfPages();
            }
            Rectangle signaturePositionOnPage = RectanguloUtil.getPositionOnPage(extraParams);
            if (typeSig != null) {
                PdfSignatureAppearance signatureAppearance = pdfSigner.getSignatureAppearance();
                signatureAppearance.setPageRect(signaturePositionOnPage).setPageNumber(page);

                if (signaturePositionOnPage != null) {
                    DatosUsuario datosUsuario = null;
                    try {
                        datosUsuario = CertEcUtils.getDatosUsuarios(x509Certificate);
                    } catch (EntidadCertificadoraNoValidaException ex) {
                        Logger.getLogger(BaseSigner.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    String nombreFirmante = (datosUsuario.getNombre() + " " + datosUsuario.getApellido()).toUpperCase();
                    String informacionCertificado = x509Certificate.getSubjectDN().getName();
                    PdfDocument pdfDocument = pdfSigner.getDocument();
                    CustomAppearance customAppearance;
                    switch (typeSig) {
                        case "QR" -> {
                            customAppearance = new QrAppereance(nombreFirmante, reason, location, signTime, infoQR);
                        }
                        case "information1" -> {
                            customAppearance = new Information1Appearance(nombreFirmante, reason, location, signTime);
                        }
                        case "information2" -> {
                            customAppearance = new Information2Appearance(nombreFirmante, informacionCertificado, reason,
                                    location, signTime);
                        }
                        default -> {
                            throw new RuntimeException("typeSign unknown");
                        }
                    }
                    customAppearance.createCustomAppearance(signatureAppearance, page, pdfDocument,
                            signaturePositionOnPage);
                }
                // Razon de firma
                if (reason != null) {
                    signatureAppearance.setReason(reason);
                }
                // Localización en donde se produce la firma
                if (location != null) {
                    signatureAppearance.setLocation(location);
                }
                signatureAppearance.setSignatureCreator(PropertiesUtils.getConfig().getProperty("version"));
                signatureAppearance.setCertificate(certChain[0]);
            }

            // Fecha y hora de la firma
            if (signTime != null) {
                Date date = Utils.getSignTime(signTime);
                GregorianCalendar calendar = new GregorianCalendar();
                calendar.setTime(date);
                pdfSigner.setSignDate(calendar);
            }
            return signInternal(os, pdfSigner, signer, certChain, params);
        }
    }

    private byte[] stripPdfAConformanceIfNeeded(byte[] pdfBytes) throws IOException {
        boolean isPdfA = false;
        try (PdfReader checkReader = new PdfReader(new ByteArrayInputStream(pdfBytes));
             PdfDocument checkDoc = new PdfDocument(checkReader)) {
            byte[] xmpBytes = checkDoc.getXmpMetadata();
            if (xmpBytes != null) {
                String xmpStr = new String(xmpBytes, StandardCharsets.UTF_8);
                isPdfA = xmpStr.contains("pdfaid");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "No se pudo verificar conformance PDF/A: {0}", e.getMessage());
            return pdfBytes;
        }

        if (!isPdfA) {
            return pdfBytes;
        }

        LOGGER.log(Level.INFO, "PDF/A detectado: removiendo declaracion de conformance para permitir firma");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfReader r = new PdfReader(new ByteArrayInputStream(pdfBytes));
             PdfWriter w = new PdfWriter(baos);
             PdfDocument doc = new PdfDocument(r, w, new StampingProperties().useAppendMode())) {
            byte[] xmpBytes = doc.getXmpMetadata();
            if (xmpBytes != null) {
                try {
                    XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(xmpBytes);
                    xmpMeta.deleteProperty(XMPConst.NS_PDFA_ID, "part");
                    xmpMeta.deleteProperty(XMPConst.NS_PDFA_ID, "conformance");
                    xmpMeta.deleteProperty(XMPConst.NS_PDFA_ID, "amd");
                    xmpMeta.deleteProperty(XMPConst.NS_PDFA_ID, "corr");
                    doc.setXmpMetadata(xmpMeta);
                } catch (XMPException e) {
                    LOGGER.log(Level.WARNING, "No se pudo modificar XMP PDF/A: {0}", e.getMessage());
                    return pdfBytes;
                }
            }
        }
        return baos.toByteArray();
    }

    protected abstract byte[] signInternal(ByteArrayOutputStream os, com.itextpdf.signatures.PdfSigner pdfSigner,
            RubricaSigner signer, Certificate[] certChain, Properties params) throws IOException;
}

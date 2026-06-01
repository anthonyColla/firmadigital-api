/*
 * Copyright (C) 2021 
 * Authors: Ricardo Arguello
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
package ec.gob.firmadigital.libreria.sign.pdf.appearance;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.signatures.PdfSignatureAppearance;

import ec.gob.firmadigital.libreria.utils.QRCode;
import static ec.gob.firmadigital.libreria.utils.Utils.loadFont;
import java.util.logging.Level;

public class QrAppereance implements CustomAppearance {

    private final String nombreFirmante;
    private final String reason;
    private final String location;
    private final String signTime;
    private final String infoQR;

    private static final Logger LOGGER = Logger.getLogger(QrAppereance.class.getName());

    public QrAppereance(String nombreFirmante, String reason, String location, String signTime, String infoQR) {
        this.nombreFirmante = nombreFirmante;
        this.reason = reason;
        this.location = location;
        
        // Si signTime es null o vacío, generar fecha actual en hora internacional (UTC)
        if (signTime == null || signTime.trim().isEmpty()) {
            this.signTime = ZonedDateTime.now(java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        } else {
            this.signTime = signTime;
        }
        
        this.infoQR = infoQR;
    }

    @Override
    public void createCustomAppearance(PdfSignatureAppearance signatureAppearance, int pageNumber,
            PdfDocument pdfDocument, Rectangle signaturePositionOnPage) throws IOException {

        signatureAppearance.setPageRect(signaturePositionOnPage);
        signatureAppearance.setPageNumber(pageNumber);

        PdfFormXObject layer2 = signatureAppearance.getLayer2();
        PdfCanvas canvas = new PdfCanvas(layer2, pdfDocument);

        PdfFont fontCourier = loadFont("fonts/courier.ttf");
        PdfFont fontCourierBold = loadFont("fonts/courier-bold.ttf");

        // QR - Generar contenido del código QR
        StringBuilder sb = new StringBuilder();
        sb.append("FIRMADO POR: ").append(nombreFirmante.trim()).append("\n");
        sb.append("RAZON: ").append(reason != null && !reason.isEmpty() ? reason : "Firmado digitalmente con Nexus Soluciones").append("\n");
        if (location != null && !location.isEmpty()) {
            sb.append("LOCALIZACION: ").append(location).append("\n");
        }
        sb.append("FECHA: ").append(signTime).append("\n");
        sb.append("VALIDAR CON: ").append("https://firmador.solucionesnexus.com").append("\n");
        sb.append(infoQR);
        String text = sb.toString();

        byte[] byteQR = null;
        try {
            byteQR = QRCode.generateQR(text, (int) signaturePositionOnPage.getHeight(),
                    (int) signaturePositionOnPage.getHeight());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al generar QR: {0}", e);
        }

        // Cargar logo Nexus desde resources
        byte[] logoBytes = null;
        try (InputStream logoStream = getClass().getClassLoader().getResourceAsStream("images/logo-nexus.png")) {
            if (logoStream != null) {
                logoBytes = logoStream.readAllBytes();
            } else {
                LOGGER.log(Level.WARNING, "No se encontró logo-nexus.png en resources/images/");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al cargar logo: {0}", e.getMessage());
        }

        float totalWidth = signaturePositionOnPage.getWidth();
        float totalHeight = signaturePositionOnPage.getHeight();

        // QR cuadrado: lado = alto disponible (85% para QR, 15% para logo)
        float logoH = totalHeight * 0.15f;
        float gap = 2f;
        float qrSide = totalHeight - logoH - gap;
        // Left column = qrSide de ancho
        float leftWidth = qrSide;

        // QR — dibujado con Canvas + scaleToFit (mantiene proporción, sin deformar)
        if (byteQR != null) {
            Rectangle qrRect = new Rectangle(0, logoH + gap, qrSide, qrSide);
            com.itextpdf.layout.element.Image qrImage = new com.itextpdf.layout.element.Image(ImageDataFactory.create(byteQR));
            qrImage.scaleToFit(qrSide, qrSide);
            qrImage.setMargins(0, 0, 0, 0);
            try (Canvas qrCanvas = new Canvas(canvas, qrRect)) {
                qrCanvas.add(qrImage);
            }
        }

        // Logo — scaleToFit mismo ancho que QR, mantiene proporción
        if (logoBytes != null) {
            Rectangle logoRect = new Rectangle(0, 0, qrSide, logoH);
            com.itextpdf.layout.element.Image logoImage = new com.itextpdf.layout.element.Image(ImageDataFactory.create(logoBytes));
            logoImage.scaleToFit(qrSide, logoH);
            logoImage.setMargins(0, 0, 0, 0);
            try (Canvas logoCanvas = new Canvas(canvas, logoRect)) {
                logoCanvas.add(logoImage);
            }
        }

        // Lado derecho: texto — pegado al QR
        float separacion = leftWidth + 3f;
        Rectangle signatureRect = new Rectangle(separacion, 0,
                totalWidth - separacion, totalHeight);

        Div textDiv = new Div();
        textDiv.setHeight(signatureRect.getHeight());
        textDiv.setWidth(signatureRect.getWidth());
        textDiv.setVerticalAlignment(VerticalAlignment.MIDDLE);
        textDiv.setHorizontalAlignment(HorizontalAlignment.LEFT);
        textDiv.setPaddingLeft(2f);

        // "Firmado electrónicamente por:"
        Text firmado = new Text("Firmado electrónicamente por:");
        Paragraph pFirmado = new Paragraph().add(firmado).setFont(fontCourier).setMargin(0)
                .setMultipliedLeading(1.0f).setFontSize(3.25f);
        textDiv.add(pFirmado);

        // Nombre en bold (wrap automático por ancho del div)
        Text contenido = new Text(nombreFirmante.trim());
        Paragraph pNombre = new Paragraph().add(contenido).setFont(fontCourierBold).setMargin(0)
                .setMultipliedLeading(0.9f).setFontSize(6.25f);
        textDiv.add(pNombre);

        // Fecha
        Text fecha = new Text("Fecha: " + signTime);
        Paragraph pFecha = new Paragraph().add(fecha).setFont(fontCourier).setMargin(0)
                .setMultipliedLeading(1.0f).setFontSize(3.25f).setPaddingTop(2f);
        textDiv.add(pFecha);

        try (Canvas textLayoutCanvas = new Canvas(canvas, signatureRect)) {
            textLayoutCanvas.add(textDiv);
        }
    }
}

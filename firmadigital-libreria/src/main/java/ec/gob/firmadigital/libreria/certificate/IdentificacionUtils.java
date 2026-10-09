/*
 * Copyright (C) 2026 Nexus Soluciones
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package ec.gob.firmadigital.libreria.certificate;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import ec.gob.firmadigital.libreria.certificate.to.DatosUsuario;

/**
 * Identificación del titular (cédula, RUC o pasaporte) sin depender de la entidad.
 * Algunas entidades guardan en el OID de "cédula" un identificador interno (UUID);
 * en ese caso la identificación real está en el serialNumber del subject
 * (RESOLUCION-ARCOTEL-2024-0176).
 */
public final class IdentificacionUtils {

    private static final Logger LOGGER = Logger.getLogger(IdentificacionUtils.class.getName());

    /** serialNumber del subject (2.5.4.5). */
    public static final String OID_SUBJECT_SERIAL_NUMBER = "2.5.4.5";

    // Cédula/RUC (10 o 13 dígitos) o pasaporte (alfanumérico corto). Un UUID no pasa: tiene guiones y 36 caracteres.
    private static final Pattern IDENTIFICACION = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    // Prefijos que algunas entidades anteponen en el serialNumber (IDCEC-, PASEC-, RUCEC-…)
    private static final Pattern PREFIJO = Pattern.compile("^[A-Za-z]{2,6}-");

    private IdentificacionUtils() {
    }

    public static boolean esIdentificacion(String valor) {
        return valor != null && IDENTIFICACION.matcher(valor.trim()).matches();
    }

    /** Quita prefijos tipo IDCEC- y espacios. */
    public static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        return PREFIJO.matcher(valor.trim()).replaceFirst("").trim();
    }

    /** Identificación tomada del serialNumber del subject, o null si no hay o no tiene forma válida. */
    public static String desdeSubject(X509Certificate certificado) {
        try {
            X500Name subject = new JcaX509CertificateHolder(certificado).getSubject();
            String serial = normalizar(CertUtils.getSubjectFieldByOID(subject, new ASN1ObjectIdentifier(OID_SUBJECT_SERIAL_NUMBER)));
            return esIdentificacion(serial) ? serial : null;
        } catch (CertificateEncodingException e) {
            LOGGER.log(Level.WARNING, "No se pudo leer el subject del certificado: {0}", e.getMessage());
            return null;
        }
    }

    /** Si la cédula leída por la entidad no es una identificación válida, la reemplaza por la del subject. */
    public static void completarCedula(DatosUsuario datosUsuario, X509Certificate certificado) {
        if (datosUsuario == null || esIdentificacion(datosUsuario.getCedula())) {
            return;
        }
        String delSubject = desdeSubject(certificado);
        if (delSubject != null) {
            LOGGER.log(Level.FINE, "Cédula tomada del subject ({0}) en vez del OID de la entidad ({1})",
                    new Object[]{delSubject, datosUsuario.getCedula()});
            datosUsuario.setCedula(delSubject);
        }
    }
}

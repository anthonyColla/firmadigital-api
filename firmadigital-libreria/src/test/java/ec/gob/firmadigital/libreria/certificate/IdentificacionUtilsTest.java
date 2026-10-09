package ec.gob.firmadigital.libreria.certificate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;

import ec.gob.firmadigital.libreria.certificate.to.DatosUsuario;

/** La identificación debe salir bien sin importar qué guarde la entidad en su OID de cédula. */
public class IdentificacionUtilsTest {

    private static final String OID_CEDULA_FIRMASEGURA = "1.3.6.1.4.1.61305.3.1";
    private static final String UUID_INTERNO = "7d849414-d8f4-4cb0-a798-aeb692a303db";

    private static X509Certificate certificado(String subjectDn, String valorOidCedula) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        X500Name subject = new X500Name(subjectDn);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                new Date(System.currentTimeMillis() - 1000L), new Date(System.currentTimeMillis() + 86_400_000L),
                subject, keys.getPublic());
        if (valorOidCedula != null) {
            builder.addExtension(new ASN1ObjectIdentifier(OID_CEDULA_FIRMASEGURA), false, new DERUTF8String(valorOidCedula));
        }
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
    }

    @Test
    public void reconoceCedulaRucYPasaporteYRechazaUuid() {
        assertTrue(IdentificacionUtils.esIdentificacion("1315354173"));
        assertTrue(IdentificacionUtils.esIdentificacion("0791843630001"));
        assertTrue(IdentificacionUtils.esIdentificacion("AB123456"));
        assertFalse(IdentificacionUtils.esIdentificacion(UUID_INTERNO));
        assertFalse(IdentificacionUtils.esIdentificacion(""));
        assertFalse(IdentificacionUtils.esIdentificacion(null));
    }

    @Test
    public void quitaPrefijosDelSerialNumber() {
        assertEquals("1315354173", IdentificacionUtils.normalizar("IDCEC-1315354173"));
        assertEquals("AB123456", IdentificacionUtils.normalizar(" PASEC-AB123456 "));
        assertEquals("1315354173", IdentificacionUtils.normalizar("1315354173"));
    }

    @Test
    public void tomaLaCedulaDelSubjectCuandoElOidTraeUnUuid() throws Exception {
        X509Certificate cert = certificado("CN=ROXANA STEFANIA MERO ANCHUNDIA,SERIALNUMBER=IDCEC-1315354173", UUID_INTERNO);
        assertEquals("1315354173", IdentificacionUtils.desdeSubject(cert));

        DatosUsuario datos = new DatosUsuario();
        datos.setCedula(CertUtils.getExtensionValue(cert, OID_CEDULA_FIRMASEGURA));
        assertEquals(UUID_INTERNO, datos.getCedula());
        IdentificacionUtils.completarCedula(datos, cert);
        assertEquals("1315354173", datos.getCedula());
    }

    @Test
    public void respetaLaCedulaCuandoLaEntidadYaLaDioBien() throws Exception {
        X509Certificate cert = certificado("CN=OTRA PERSONA,SERIALNUMBER=9999999999", "1315354173");
        DatosUsuario datos = new DatosUsuario();
        datos.setCedula("1315354173");
        IdentificacionUtils.completarCedula(datos, cert);
        assertEquals("1315354173", datos.getCedula());
    }

    @Test
    public void sinSerialNumberNoInventaNada() throws Exception {
        X509Certificate cert = certificado("CN=SIN SERIAL", UUID_INTERNO);
        assertNull(IdentificacionUtils.desdeSubject(cert));
        DatosUsuario datos = new DatosUsuario();
        datos.setCedula(UUID_INTERNO);
        IdentificacionUtils.completarCedula(datos, cert);
        assertEquals(UUID_INTERNO, datos.getCedula());
    }
}

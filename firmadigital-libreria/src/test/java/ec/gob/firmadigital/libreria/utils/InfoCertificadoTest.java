package ec.gob.firmadigital.libreria.utils;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;

import ec.gob.firmadigital.libreria.certificate.to.DatosUsuario;
import ec.gob.firmadigital.libreria.sign.SignInfo;

/** El subject DN solo completa lo que falta: nunca pisa la cédula que ya dio la entidad. */
public class InfoCertificadoTest {

    private static SignInfo signInfo(String subjectDn) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        X500Name subject = new X500Name(subjectDn);
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(subject, BigInteger.ONE, new Date(System.currentTimeMillis() - 1000L),
                        new Date(System.currentTimeMillis() + 86_400_000L), subject, keys.getPublic())
                        .build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
        return new SignInfo(new X509Certificate[]{cert}, new Date());
    }

    @Test
    public void noPisaLaCedulaDeLaEntidadConElUuidDelSubject() throws Exception {
        DatosUsuario datos = new DatosUsuario();
        datos.setCedula("1315354173");
        datos.setNombre("ROXANA STEFANIA");
        Utils.infoCertificado(datos, signInfo("CN=ROXANA STEFANIA MERO ANCHUNDIA,SERIALNUMBER=7d849414-d8f4-4cb0-a798-aeb692a303db,O=FIRMASEGURA S.A.S."));
        assertEquals("1315354173", datos.getCedula());
        assertEquals("ROXANA STEFANIA", datos.getNombre());
        assertEquals("FIRMASEGURA S.A.S.", datos.getInstitucion());
    }

    @Test
    public void sinDatosDeLaEntidadTomaElSubject() throws Exception {
        DatosUsuario datos = new DatosUsuario();
        Utils.infoCertificado(datos, signInfo("CN=JUAN PEREZ,SERIALNUMBER=IDCEC-0102030405,O=OTRA CA"));
        assertEquals("0102030405", datos.getCedula());
        assertEquals("JUAN PEREZ", datos.getNombre());
    }

    @Test
    public void sinCedulaValidaEnNingunLadoDejaLaDelSubject() throws Exception {
        DatosUsuario datos = new DatosUsuario();
        Utils.infoCertificado(datos, signInfo("CN=SIN CEDULA,SERIALNUMBER=7d849414-d8f4-4cb0-a798-aeb692a303db"));
        assertEquals("7d849414-d8f4-4cb0-a798-aeb692a303db", datos.getCedula());
    }
}

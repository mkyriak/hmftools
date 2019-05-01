package com.hartwig.hmftools.patientreporter.cfreport.components;

import com.hartwig.hmftools.patientreporter.cfreport.ReportResources;
import com.itextpdf.io.IOException;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;

public class ReportSignature {

    @NotNull
    public static Div createSignatureDiv(@NotNull String rvaLogoPath, @NotNull String signaturePath) throws IOException {

            Div div = new Div();
            div.setKeepTogether(true);

            // Add RVA logo
            try {
                final Image rvaLogo = new Image(ImageDataFactory.create(rvaLogoPath));
                rvaLogo.setMaxHeight(58);
                div.add(rvaLogo);
            } catch (MalformedURLException e) {
                throw new IOException("Failed to read RVA logo image at " + rvaLogoPath);
            }

            // Add signature text
            Paragraph signatureText = new Paragraph()
                    .setFont(ReportResources.getFontBold())
                    .setFontSize(10)
                    .setFontColor(ReportResources.PALETTE_BLACK);

            signatureText.add(ReportResources.SIGNATURE_NAME + ",\n");
            signatureText.add(new Text(ReportResources.SIGNATURE_TITLE).setFont(ReportResources.getFontRegular()));
            div.add(signatureText);

            // Add signature image
            try {
                final Image signatureImage = new Image(ImageDataFactory.create(signaturePath));
                signatureImage.setMaxHeight(60);
                signatureImage.setMarginTop(-20); // Set negative margin so the signature slightly overlaps the signature text
                signatureImage.setMarginLeft(10);
                div.add(signatureImage);
            } catch (MalformedURLException e) {
                throw new IOException("Failed to read signature image at " + signaturePath);
            }

            return div;

    }

}

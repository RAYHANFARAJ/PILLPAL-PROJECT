package com.pillpall.med_application.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    public Map<String, String> extractFromImage(MultipartFile file, String patientId) {
        ITesseract tesseract = new Tesseract();
        try {
            // Vérifier le dossier tessdata
            String tessdataPath = "C:\\Program Files\\Tesseract-OCR\\tessdata";
            if (!Files.exists(Paths.get(tessdataPath, "fra.traineddata"))) {
                System.err.println("Erreur: fra.traineddata introuvable dans " + tessdataPath);
                throw new RuntimeException("Fichier fra.traineddata manquant dans " + tessdataPath);
            }
            System.out.println("Configuration de Tesseract...");
            tesseract.setDatapath(tessdataPath);
            tesseract.setLanguage("fra");
            tesseract.setTessVariable("tessedit_pageseg_mode", "6"); // Mode PSM 6 pour bloc unique
            System.out.println("Tesseract configuré avec datapath: " + tessdataPath + ", langue: fra, PSM: 6");

            // Vérifier le fichier reçu
            if (file == null || file.isEmpty()) {
                System.err.println("Erreur: Aucun fichier reçu ou fichier vide");
                throw new RuntimeException("Aucun fichier image reçu");
            }
            System.out.println("Fichier reçu: nom=" + file.getOriginalFilename() + ", taille=" + file.getSize() + " bytes, type=" + file.getContentType());

            // Créer un fichier temporaire
            File tempFile = File.createTempFile("scan_", ".jpg");
            System.out.println("Fichier temporaire créé: " + tempFile.getAbsolutePath());
            file.transferTo(tempFile);

            // Vérifier les permissions d'écriture
            if (!tempFile.canWrite()) {
                System.err.println("Erreur: Pas de permission d'écriture pour " + tempFile.getAbsolutePath());
                throw new RuntimeException("Pas de permission d'écriture pour le fichier temporaire");
            }

            // Effectuer l'OCR
            String text = tesseract.doOCR(tempFile);
            System.out.println("Texte brut OCR: [" + text + "]");
            System.out.println("Longueur du texte brut OCR: " + text.length());

            // Nettoyer le fichier temporaire
            if (tempFile.delete()) {
                System.out.println("Fichier temporaire supprimé avec succès");
            } else {
                System.out.println("Échec de la suppression du fichier temporaire");
            }

            // Extraire les champs avec regex
            Map<String, String> extracted = new HashMap<>();
            extracted.put("patientId", patientId);
            extracted.put("medicationName", extractField(text, "(?:M.dicament|Médicament|Medication)\\s*[:=]\\s*(.+)"));
            extracted.put("dosage", extractField(text, "(?:Dosage)\\s*[:=]\\s*(.+)"));
            extracted.put("startDate", extractField(text, "(?:D.but|Début|Start)\\s*[:=]\\s*(\\d{4}-\\d{2}-\\d{2})"));
            extracted.put("endDate", extractField(text, "(?:Fin|End)\\s*[:=]\\s*(\\d{4}-\\d{2}-\\d{2})"));
            extracted.put("doseTimes", extractField(text, "(?:Horaires|Times)\\s*[:=]\\s*(.+)"));
            System.out.println("Champs extraits envoyés au frontend: " + extracted);

            // Vérifier si tous les champs sont vides
            boolean allFieldsEmpty = extracted.entrySet().stream()
                    .filter(e -> !e.getKey().equals("patientId"))
                    .allMatch(e -> e.getValue() == null || e.getValue().isEmpty());
            if (allFieldsEmpty) {
                System.out.println("AVERTISSEMENT: Tous les champs extraits (sauf patientId) sont vides !");
            }

            return extracted;
        } catch (IOException e) {
            System.err.println("Erreur IO lors de l'OCR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("OCR échoué à cause d'une erreur IO", e);
        } catch (TesseractException e) {
            System.err.println("Erreur Tesseract: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("OCR échoué à cause d'une erreur Tesseract", e);
        } catch (Exception e) {
            System.err.println("Erreur inattendue lors de l'OCR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("OCR échoué à cause d'une erreur inattendue", e);
        }
    }

    private String extractField(String text, String regex) {
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String result = matcher.group(1).trim();
                System.out.println("Regex correspondante pour le motif [" + regex + "]: " + result);
                return result;
            } else {
                System.out.println("Aucune correspondance pour le motif regex [" + regex + "]");
                return "";
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la correspondance regex pour le motif [" + regex + "]: " + e.getMessage());
            return "";
        }
    }
}
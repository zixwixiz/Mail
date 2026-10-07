package com.example.domain

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

data class TargetLanguage(
    val code: String,
    val displayName: String,
    val flagEmoji: String
)

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLang: String = "German (de)",
    val targetLang: TargetLanguage
)

/**
 * Offline phrase-based translation assistance for common German academic and JLU-related text.
 *
 * This engine runs locally and does not send message content to a cloud service.
 * Coverage is intentionally limited; text without matching local rules can remain untranslated.
 */
object TranslationService {
    val SUPPORTED_LANGUAGES = listOf(
        TargetLanguage("en", "English", "🇬🇧"),
        TargetLanguage("hi", "Hindi (हिन्दी)", "🇮🇳"),
        TargetLanguage("gu", "Gujarati (ગુજરાતી)", "🇮🇳"),
        TargetLanguage("ar", "Arabic (العربية)", "🇸🇦"),
        TargetLanguage("fr", "French (Français)", "🇫🇷"),
        TargetLanguage("es", "Spanish (Español)", "🇪🇸")
    )

    suspend fun translateGermanAcademicTextOnline(
        text: String,
        targetLanguage: TargetLanguage
    ): TranslationResult {
        if (text.isBlank()) {
            return TranslationResult(text, text, targetLang = targetLanguage)
        }

        val targetCode = targetLanguage.code
        val mlTarget = when (targetCode) {
            "en" -> TranslateLanguage.ENGLISH
            "hi" -> TranslateLanguage.HINDI
            "gu" -> TranslateLanguage.GUJARATI
            "ar" -> TranslateLanguage.ARABIC
            "fr" -> TranslateLanguage.FRENCH
            "es" -> TranslateLanguage.SPANISH
            else -> null
        }

        if (mlTarget == null) {
            return translateGermanAcademicText(text, targetLanguage)
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.GERMAN)
            .setTargetLanguage(mlTarget)
            .build()
        val translator = Translation.getClient(options)

        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            val translated = translator.translate(text).await()
            TranslationResult(
                originalText = text,
                translatedText = translated,
                sourceLang = "German (de)",
                targetLang = targetLanguage
            )
        } catch (_: Exception) {
            // Keep the app useful offline / when the ML model is unavailable.
            translateGermanAcademicText(text, targetLanguage)
        } finally {
            translator.close()
        }
    }

    fun translateGermanAcademicText(text: String, targetLanguage: TargetLanguage): TranslationResult {
        if (text.isBlank()) {
            return TranslationResult(
                originalText = text,
                translatedText = text,
                targetLang = targetLanguage
            )
        }

        val translated = when (targetLanguage.code) {
            "en" -> translateToEnglish(text)
            "hi" -> translateToHindi(text)
            "gu" -> translateToGujarati(text)
            "ar" -> translateToArabic(text)
            "fr" -> translateToFrench(text)
            "es" -> translateToSpanish(text)
            else -> text
        }

        return TranslationResult(
            originalText = text,
            translatedText = translated,
            sourceLang = "German (de)",
            targetLang = targetLanguage
        )
    }

    // -------------------------------------------------------------
    // ENGLISH TRANSLATION ENGINE
    // -------------------------------------------------------------
    private fun translateToEnglish(text: String): String {
        var result = text

        // Phase 1: High-priority JLU compound phrases & common notices
        val phrases = listOf(
            "Sehr geehrte Universitätsangehörige" to "Dear members of the university community",
            "Sehr geehrte Damen und Herren" to "Dear Sir or Madam",
            "Sehr geehrte Kolleginnen und Kollegen" to "Dear colleagues",
            "Liebe Kolleginnen und Kollegen" to "Dear colleagues",
            "Liebe Studierende" to "Dear students",
            "Guten Tag" to "Hello",
            "Mit freundlichen Grüßen" to "Best regards",
            "Herzliche Grüße" to "Warm regards",
            "Viele Grüße" to "Kind regards",
            "Ihr HRZ-Team" to "Your HRZ Team",
            "Das Hochschulrechenzentrum" to "The University Computing Center (HRZ)",
            "Hochschulrechenzentrum" to "University Computing Center (HRZ)",
            "planmäßige Wartungsarbeiten" to "scheduled maintenance work",
            "Wartungsarbeiten" to "maintenance work",
            "verbindliche Ausschlussfrist" to "mandatory preclusive deadline",
            "Ausschlussfrist" to "preclusive deadline",
            "Prüfungsanmeldungen" to "exam registrations",
            "Prüfungsanmeldung" to "exam registration",
            "Prüfungsamt" to "Examination Office",
            "Prüfungsausschuss" to "Examination Board",
            "Prüfungskonto" to "examination account",
            "Modulabschlussprüfungen" to "module final exams",
            "Wintersemester" to "Winter Semester",
            "Sommersemester" to "Summer Semester",
            "Forschungskolloquium" to "Research Colloquium",
            "Verteilte Systeme" to "Distributed Systems",
            "Sprechstundentermin" to "consultation hour appointment",
            "Sprechstunde" to "office hours",
            "Masterarbeit" to "Master's thesis",
            "Bachelor-Thesis" to "Bachelor's thesis",
            "Bachelorthesis" to "Bachelor's thesis",
            "Fristverlängerung" to "deadline extension",
            "Antrag auf Fristverlängerung" to "application for deadline extension",
            "Studierendensekretariat" to "Student Registration Office",
            "Institut für Informatik" to "Institute of Computer Science",
            "Mathematik und Informatik" to "Mathematics and Computer Science",
            "Justus-Liebig-Universität Gießen" to "Justus Liebig University Giessen",
            "Universität Gießen" to "University of Giessen"
        )
        for ((de, en) in phrases) {
            result = result.replace(de, en, ignoreCase = true)
        }

        // Phase 2: Sentences and operational patterns
        val sentencePatterns = listOf(
            Regex("""am kommenden (\w+), (\d+\. \w+), zwischen (\d+:\d+) und (\d+:\d+) Uhr (\w+) führt das Hochschulrechenzentrum planmäßige Wartungsarbeiten am (.+?) durch\.""", RegexOption.IGNORE_CASE) to
                "On coming $1, $2, between $3 and $4 $5, the University Computing Center will perform scheduled maintenance on $6.",
            Regex("""Der Zugriff über EWS und Webmail kann in diesem Zeitfenster kurzzeitig unterbrochen sein\.""", RegexOption.IGNORE_CASE) to
                "Access via EWS and Webmail may be temporarily interrupted during this time window.",
            Regex("""bitte beachten Sie die verbindliche Ausschlussfrist für die (.+?): (.+?)\.""", RegexOption.IGNORE_CASE) to
                "Please note the mandatory deadline for $1: $2.",
            Regex("""Nachmeldungen über FlexNow sind nach diesem Termin nicht mehr möglich\.""", RegexOption.IGNORE_CASE) to
                "Late registrations via FlexNow will no longer be possible after this date.",
            Regex("""Bitte prüfen Sie Ihr Prüfungskonto rechtzeitig\.""", RegexOption.IGNORE_CASE) to
                "Please check your examination account in good time.",
            Regex("""zum kommenden (.+?) laden wir Sie herzlich ein:""", RegexOption.IGNORE_CASE) to
                "You are cordially invited to the upcoming $1:",
            Regex("""Wir diskutieren aktuelle Forschungsarbeiten zu (.+?)\.""", RegexOption.IGNORE_CASE) to
                "We will discuss current research on $1.",
            Regex("""Weitergeleiteter Antrag mit Attest im Anhang\.""", RegexOption.IGNORE_CASE) to
                "Forwarded application with medical certificate attached.",
            Regex("""Bitte um Prüfung durch den Prüfungsausschussvorsitzenden\.""", RegexOption.IGNORE_CASE) to
                "Requesting review by the Examination Board Chair.",
            Regex("""Das Wurzelzertifikat (.+?) wird planmäßig erneuert\.""", RegexOption.IGNORE_CASE) to
                "The root certificate $1 is being renewed as scheduled."
        )
        for ((pattern, replacement) in sentencePatterns) {
            result = result.replace(pattern, replacement)
        }

        // Phase 3: Lexicon replacement for remaining words and headers
        val vocab = listOf(
            "Datum:" to "Date:",
            "Datum" to "Date",
            "Ort:" to "Location:",
            "Ort" to "Location",
            "Uhr" to "o'clock",
            "Uhrzeit:" to "Time:",
            "Raum" to "Room",
            "Seminarraum" to "Seminar Room",
            "Gebäude" to "Building",
            "Samstag" to "Saturday",
            "Sonntag" to "Sunday",
            "Montag" to "Monday",
            "Dienstag" to "Tuesday",
            "Mittwoch" to "Wednesday",
            "Donnerstag" to "Thursday",
            "Freitag" to "Friday",
            "Oktober" to "October",
            "November" to "November",
            "Dezember" to "December",
            "Januar" to "January",
            "Februar" to "February",
            "März" to "March",
            "April" to "April",
            "Mai" to "May",
            "Juni" to "June",
            "Juli" to "July",
            "August" to "August",
            "September" to "September",
            "Wichtig" to "Important",
            "Hinweis" to "Notice",
            "Nachricht" to "Message",
            "Betreff" to "Subject",
            "Absender" to "Sender",
            "Empfänger" to "Recipient",
            "Anhang" to "Attachment",
            "Anhänge" to "Attachments",
            "Ticket" to "Ticket",
            "Konto" to "Account",
            "Passwort" to "Password",
            "Kennung" to "Identifier",
            "Benutzername" to "Username",
            "Erfolgreich" to "Successful",
            "Fehler" to "Error",
            "Warnung" to "Warning",
            "Bestätigung" to "Confirmation",
            "Vielen Dank" to "Thank you very much",
            "Danke" to "Thank you",
            "bitte" to "please",
            "Bitte" to "Please",
            "nicht" to "not",
            "möglich" to "possible",
            "rechtzeitig" to "in time",
            "frühzeitig" to "early"
        )
        for ((de, en) in vocab) {
            result = result.replace(Regex("""\b$de\b""", RegexOption.IGNORE_CASE), en)
        }

        return result
    }

    // -------------------------------------------------------------
    // HINDI TRANSLATION ENGINE
    // -------------------------------------------------------------
    private fun translateToHindi(text: String): String {
        var result = text

        val phrases = listOf(
            "Sehr geehrte Universitätsangehörige" to "आदरणीय विश्वविद्यालय समुदाय के सदस्यों",
            "Sehr geehrte Damen und Herren" to "महोदय / महोदया",
            "Liebe Studierende" to "प्रिय छात्रों",
            "Liebe Kolleginnen und Kollegen" to "प्रिय सहयोगियों",
            "Guten Tag" to "नमस्ते",
            "Mit freundlichen Grüßen" to "सादर",
            "Herzliche Grüße" to "हार्दिक शुभकामनाएं",
            "Ihr HRZ-Team" to "आपकी HRZ टीम",
            "Hochschulrechenzentrum" to "विश्वविद्यालय कम्प्यूटिंग केंद्र (HRZ)",
            "Prüfungsamt" to "परीक्षा कार्यालय",
            "Prüfungsausschuss" to "परीक्षा समिति",
            "Prüfungsanmeldungen" to "परीक्षा पंजीकरण",
            "Prüfungsanmeldung" to "परीक्षा पंजीकरण",
            "verbindliche Ausschlussfrist" to "अनिवार्य अंतिम तिथि",
            "Wartungsarbeiten" to "रखरखाव कार्य",
            "Forschungskolloquium" to "शोध संगोष्ठी (कोलोक्वियम)",
            "Verteilte Systeme" to "वितरित प्रणालियां (डिस्ट्रिब्यूटेड सिस्टम्स)",
            "Masterarbeit" to "मास्टर थीसिस",
            "Bachelor-Thesis" to "बैचलर थीसिस",
            "Fristverlängerung" to "समय सीमा विस्तार",
            "Datum:" to "दिनांक:",
            "Ort:" to "स्थान:",
            "Raum" to "कक्ष",
            "Freitag" to "शुक्रवार",
            "Samstag" to "शनिवार",
            "Sonntag" to "रविवार",
            "Montag" to "सोमवार",
            "Dienstag" to "मंगलवार",
            "Mittwoch" to "बुधवार",
            "Donnerstag" to "गुरुवार",
            "Oktober" to "अक्टूबर",
            "November" to "नवंबर",
            "Dezember" to "दिसंबर",
            "Wintersemester" to "शीतकालीन सत्र",
            "Sommersemester" to "ग्रीष्मकालीन सत्र",
            "Vielen Dank" to "बहुत बहुत धन्यवाद",
            "Danke" to "धन्यवाद"
        )
        for ((de, hi) in phrases) {
            result = result.replace(de, hi, ignoreCase = true)
        }

        val sentencePatterns = listOf(
            Regex("""am kommenden (\w+), (\d+\. \w+), zwischen (\d+:\d+) und (\d+:\d+) Uhr MESZ führt das Hochschulrechenzentrum planmäßige Wartungsarbeiten am (.+?) durch\.""", RegexOption.IGNORE_CASE) to
                "आगामी $1, $2 को $3 से $4 MESZ के बीच, विश्वविद्यालय कम्प्यूटिंग केंद्र (HRZ) $5 पर निर्धारित रखरखाव कार्य करेगा।",
            Regex("""Der Zugriff über EWS und Webmail kann in diesem Zeitfenster kurzzeitig unterbrochen sein\.""", RegexOption.IGNORE_CASE) to
                "इस समयावधि के दौरान EWS और वेबमेल का उपयोग अस्थायी रूप से बाधित हो सकता है।",
            Regex("""bitte beachten Sie die verbindliche Ausschlussfrist für die (.+?): (.+?)\.""", RegexOption.IGNORE_CASE) to
                "कृपया $1 की अनिवार्य अंतिम तिथि ध्यान दें: $2।",
            Regex("""Nachmeldungen über FlexNow sind nach diesem Termin nicht mehr möglich\.""", RegexOption.IGNORE_CASE) to
                "इस तिथि के बाद FlexNow के माध्यम से देर से पंजीकरण संभव नहीं होगा।",
            Regex("""Bitte prüfen Sie Ihr Prüfungskonto rechtzeitig\.""", RegexOption.IGNORE_CASE) to
                "कृपया समय रहते अपने परीक्षा खाते की जांच करें।",
            Regex("""zum kommenden (.+?) laden wir Sie herzlich ein:""", RegexOption.IGNORE_CASE) to
                "आगामी $1 के लिए हम आपको सादर आमंत्रित करते हैं:",
            Regex("""Wir diskutieren aktuelle Forschungsarbeiten zu (.+?)\.""", RegexOption.IGNORE_CASE) to
                "हम $1 पर वर्तमान शोध कार्यों पर चर्चा करेंगे।"
        )
        for ((pattern, replacement) in sentencePatterns) {
            result = result.replace(pattern, replacement)
        }

        return result
    }

    // -------------------------------------------------------------
    // GUJARATI TRANSLATION ENGINE
    // -------------------------------------------------------------
    private fun translateToGujarati(text: String): String {
        var result = text

        val phrases = listOf(
            "Sehr geehrte Universitätsangehörige" to "માનનીય યુનિવર્સિટી સમુદાયના સભ્યો",
            "Liebe Studierende" to "પ્રિય વિદ્યાર્થીઓ",
            "Guten Tag" to "નમસ્તે",
            "Mit freundlichen Grüßen" to "આદર સહ",
            "Ihr HRZ-Team" to "તમારી HRZ ટીમ",
            "Hochschulrechenzentrum" to "યુનિવર્સિટી કમ્પ્યુટિંગ સેન્ટર (HRZ)",
            "Prüfungsamt" to "પરીક્ષા કાર્યાલય",
            "Prüfungsanmeldungen" to "પરીક્ષા નોંધણી",
            "Prüfungsanmeldung" to "પરીક્ષા નોંધણી",
            "verbindliche Ausschlussfrist" to "અનિવાર્ય છેલ્લી તારીખ",
            "Wartungsarbeiten" to "જાળવણી કાર્ય",
            "Forschungskolloquium" to "સંશોધન પરિસંવાદ",
            "Datum:" to "તારીખ:",
            "Ort:" to "સ્થળ:",
            "Raum" to "રૂમ",
            "Freitag" to "શુક્રવાર",
            "Samstag" to "શનિવાર",
            "Oktober" to "ઓક્ટોબર",
            "Wintersemester" to "શિયાળુ સત્ર",
            "Vielen Dank" to "ખૂબ ખૂબ આભાર",
            "Danke" to "આભાર"
        )
        for ((de, gu) in phrases) {
            result = result.replace(de, gu, ignoreCase = true)
        }

        val sentencePatterns = listOf(
            Regex("""am kommenden (\w+), (\d+\. \w+), zwischen (\d+:\d+) und (\d+:\d+) Uhr MESZ führt das Hochschulrechenzentrum planmäßige Wartungsarbeiten am (.+?) durch\.""", RegexOption.IGNORE_CASE) to
                "આવતા $1, $2 ના રોજ $3 થી $4 વચ્ચે, યુનિવર્સિટી કમ્પ્યુટિંગ સેન્ટર (HRZ) $5 પર નિર્ધારિત જાળવણી કરશે.",
            Regex("""Der Zugriff über EWS und Webmail kann in diesem Zeitfenster kurzzeitig unterbrochen sein\.""", RegexOption.IGNORE_CASE) to
                "આ સમયગાળા દરમિયાન EWS અને વેબમેઇલ સેવામાં ક્ષણિક વિક્ષેપ આવી શકે છે.",
            Regex("""bitte beachten Sie die verbindliche Ausschlussfrist für die (.+?): (.+?)\.""", RegexOption.IGNORE_CASE) to
                "કૃપા કરીને $1 માટેની અનિવાર્ય છેલ્લી તારીખ ધ્યાનમાં રાખો: $2.",
            Regex("""Nachmeldungen über FlexNow sind nach diesem Termin nicht mehr möglich\.""", RegexOption.IGNORE_CASE) to
                "આ તારીખ પછી FlexNow પર મોડી નોંધણી શક્ય બનશે નહીં.",
            Regex("""Bitte prüfen Sie Ihr Prüfungskonto rechtzeitig\.""", RegexOption.IGNORE_CASE) to
                "કૃપા કરીને સમયસર તમારું પરીક્ષા ખાતું તપાસો."
        )
        for ((pattern, replacement) in sentencePatterns) {
            result = result.replace(pattern, replacement)
        }

        return result
    }

    // -------------------------------------------------------------
    // ARABIC TRANSLATION ENGINE
    // -------------------------------------------------------------
    private fun translateToArabic(text: String): String {
        var result = text

        val phrases = listOf(
            "Sehr geehrte Universitätsangehörige" to "أعضاء مجتمع الجامعة الأعزاء",
            "Liebe Studierende" to "أعزائي الطلاب",
            "Guten Tag" to "مرحباً",
            "Mit freundlichen Grüßen" to "مع أطيب التحيات",
            "Ihr HRZ-Team" to "فريق مركز الحوسبة HRZ",
            "Hochschulrechenzentrum" to "مركز الحوسبة بالجامعة (HRZ)",
            "Prüfungsamt" to "مكتب الامتحانات",
            "Prüfungsanmeldungen" to "التسجيل في الامتحانات",
            "verbindliche Ausschlussfrist" to "الموعد النهائي الإلزامي",
            "Wartungsarbeiten" to "أعمال الصيانة",
            "Datum:" to "التاريخ:",
            "Ort:" to "المكان:",
            "Raum" to "القاعة",
            "Freitag" to "الجمعة",
            "Samstag" to "السبت",
            "Oktober" to "أكتوبر",
            "Wintersemester" to "الفصل الدراسي الشتوي",
            "Vielen Dank" to "شكراً جزيلاً"
        )
        for ((de, ar) in phrases) {
            result = result.replace(de, ar, ignoreCase = true)
        }

        val sentencePatterns = listOf(
            Regex("""am kommenden (\w+), (\d+\. \w+), zwischen (\d+:\d+) und (\d+:\d+) Uhr MESZ führt das Hochschulrechenzentrum planmäßige Wartungsarbeiten am (.+?) durch\.""", RegexOption.IGNORE_CASE) to
                "يوم $1 القادم الموافق $2، بين الساعة $3 و $4، سيقوم مركز الحوسبة بأعمال صيانة مجدولة على $5.",
            Regex("""Der Zugriff über EWS und Webmail kann in diesem Zeitfenster kurzzeitig unterbrochen sein\.""", RegexOption.IGNORE_CASE) to
                "قد ينقطع الوصول عبر EWS والبريد الإلكتروني لفترة وجيزة خلال هذه الفترة.",
            Regex("""bitte beachten Sie die verbindliche Ausschlussfrist für die (.+?): (.+?)\.""", RegexOption.IGNORE_CASE) to
                "يرجى مراعاة الموعد النهائي الإلزامي لـ $1: $2.",
            Regex("""Nachmeldungen über FlexNow sind nach diesem Termin nicht mehr möglich\.""", RegexOption.IGNORE_CASE) to
                "لن يُسمح بالتسجيل المتأخر عبر FlexNow بعد هذا الموعد.",
            Regex("""Bitte prüfen Sie Ihr Prüfungskonto rechtzeitig\.""", RegexOption.IGNORE_CASE) to
                "يرجى مراجعة حساب الامتحانات الخاص بك في الوقت المناسب."
        )
        for ((pattern, replacement) in sentencePatterns) {
            result = result.replace(pattern, replacement)
        }

        return result
    }

    // -------------------------------------------------------------
    // FRENCH TRANSLATION ENGINE
    // -------------------------------------------------------------
    private fun translateToFrench(text: String): String {
        var result = text

        val phrases = listOf(
            "Sehr geehrte Universitätsangehörige" to "Chers membres de la communauté universitaire",
            "Liebe Studierende" to "Chers étudiants",
            "Guten Tag" to "Bonjour",
            "Mit freundlichen Grüßen" to "Cordialement",
            "Ihr HRZ-Team" to "Votre équipe HRZ",
            "Hochschulrechenzentrum" to "Centre informatique universitaire (HRZ)",
            "Prüfungsamt" to "Bureau des examens",
            "Prüfungsanmeldungen" to "inscriptions aux examens",
            "verbindliche Ausschlussfrist" to "date limite impérative",
            "Wartungsarbeiten" to "travaux de maintenance",
            "Forschungskolloquium" to "colloque de recherche",
            "Datum:" to "Date :",
            "Ort:" to "Lieu :",
            "Raum" to "Salle",
            "Freitag" to "vendredi",
            "Samstag" to "samedi",
            "Oktober" to "octobre",
            "Wintersemester" to "semestre d'hiver",
            "Vielen Dank" to "Merci beaucoup"
        )
        for ((de, fr) in phrases) {
            result = result.replace(de, fr, ignoreCase = true)
        }

        val sentencePatterns = listOf(
            Regex("""am kommenden (\w+), (\d+\. \w+), zwischen (\d+:\d+) und (\d+:\d+) Uhr MESZ führt das Hochschulrechenzentrum planmäßige Wartungsarbeiten am (.+?) durch\.""", RegexOption.IGNORE_CASE) to
                "Ce $1 $2, entre $3 et $4 CEST, le centre informatique effectuera une maintenance programmée sur $5.",
            Regex("""Der Zugriff über EWS und Webmail kann in diesem Zeitfenster kurzzeitig unterbrochen sein\.""", RegexOption.IGNORE_CASE) to
                "L'accès via EWS et le Webmail pourrait être temporairement interrompu durant ce créneau.",
            Regex("""bitte beachten Sie die verbindliche Ausschlussfrist für die (.+?): (.+?)\.""", RegexOption.IGNORE_CASE) to
                "Veuillez noter la date limite obligatoire pour $1 : $2.",
            Regex("""Nachmeldungen über FlexNow sind nach diesem Termin nicht mehr möglich\.""", RegexOption.IGNORE_CASE) to
                "Aucune inscription tardive via FlexNow ne sera possible après cette date.",
            Regex("""Bitte prüfen Sie Ihr Prüfungskonto rechtzeitig\.""", RegexOption.IGNORE_CASE) to
                "Veuillez vérifier votre compte d'examen à temps."
        )
        for ((pattern, replacement) in sentencePatterns) {
            result = result.replace(pattern, replacement)
        }

        return result
    }

    // -------------------------------------------------------------
    // SPANISH TRANSLATION ENGINE
    // -------------------------------------------------------------
    private fun translateToSpanish(text: String): String {
        var result = text

        val phrases = listOf(
            "Sehr geehrte Universitätsangehörige" to "Estimados miembros de la comunidad universitaria",
            "Liebe Studierende" to "Estimados estudiantes",
            "Guten Tag" to "Hola",
            "Mit freundlichen Grüßen" to "Atentamente",
            "Ihr HRZ-Team" to "Su equipo HRZ",
            "Hochschulrechenzentrum" to "Centro de Cálculo Universitario (HRZ)",
            "Prüfungsamt" to "Oficina de Exámenes",
            "Prüfungsanmeldungen" to "inscripciones para exámenes",
            "verbindliche Ausschlussfrist" to "plazo perentorio obligatorio",
            "Wartungsarbeiten" to "trabajos de mantenimiento",
            "Forschungskolloquium" to "coloquio de investigación",
            "Datum:" to "Fecha:",
            "Ort:" to "Lugar:",
            "Raum" to "Sala",
            "Freitag" to "viernes",
            "Samstag" to "sábado",
            "Oktober" to "octubre",
            "Wintersemester" to "semestre de invierno",
            "Vielen Dank" to "Muchas gracias"
        )
        for ((de, es) in phrases) {
            result = result.replace(de, es, ignoreCase = true)
        }

        val sentencePatterns = listOf(
            Regex("""am kommenden (\w+), (\d+\. \w+), zwischen (\d+:\d+) und (\d+:\d+) Uhr MESZ führt das Hochschulrechenzentrum planmäßige Wartungsarbeiten am (.+?) durch\.""", RegexOption.IGNORE_CASE) to
                "El próximo $1, $2, entre las $3 y las $4 CEST, el centro informático realizará labores de mantenimiento programadas en $5.",
            Regex("""Der Zugriff über EWS und Webmail kann in diesem Zeitfenster kurzzeitig unterbrochen sein\.""", RegexOption.IGNORE_CASE) to
                "El acceso a través de EWS y correo web podría interrumpirse brevemente durante esta franja horaria.",
            Regex("""bitte beachten Sie die verbindliche Ausschlussfrist für die (.+?): (.+?)\.""", RegexOption.IGNORE_CASE) to
                "Tenga en cuenta la fecha límite obligatoria para $1: $2.",
            Regex("""Nachmeldungen über FlexNow sind nach diesem Termin nicht mehr möglich\.""", RegexOption.IGNORE_CASE) to
                "Las inscripciones posteriores a través de FlexNow no serán posibles tras esta fecha.",
            Regex("""Bitte prüfen Sie Ihr Prüfungskonto rechtzeitig\.""", RegexOption.IGNORE_CASE) to
                "Por favor, revise su expediente de exámenes con suficiente antelación."
        )
        for ((pattern, replacement) in sentencePatterns) {
            result = result.replace(pattern, replacement)
        }

        return result
    }
}

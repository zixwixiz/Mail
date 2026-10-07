package com.example.data.network

import com.example.data.local.entities.CalendarEventEntity
import com.example.data.local.entities.ContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class EwsCalendarSyncResult(val isSuccess: Boolean, val events: List<CalendarEventEntity>, val message: String)
data class EwsContactSyncResult(val isSuccess: Boolean, val contacts: List<ContactEntity>, val message: String)

class EwsMailboxSyncClient(private val http: ExchangeHttpClient = ExchangeHttpClient()) {
    suspend fun fetchCalendar(endpoint: String, username: String, password: String, mailboxId: String, mailboxEmail: String): EwsCalendarSyncResult =
        withContext(Dispatchers.IO) {
            val start = utc(System.currentTimeMillis())
            val end = utc(System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000)
            val soap = calendarSoap(mailboxEmail, start, end)
            runCatching {
                val response = http.postSoap(endpoint, username, password, "http://schemas.microsoft.com/exchange/services/2006/messages/FindItem", soap, mailboxEmail)
                val events = parseCalendar(response.body, mailboxId)
                val code = response.body.responseCode()
                EwsCalendarSyncResult(response.statusCode in 200..299 && code == "NoError", events, code.ifBlank { "HTTP " + response.statusCode })
            }.getOrElse { EwsCalendarSyncResult(false, emptyList(), it.localizedMessage ?: "Calendar sync failed") }
        }

    suspend fun fetchContacts(endpoint: String, username: String, password: String, mailboxId: String, mailboxEmail: String): EwsContactSyncResult =
        withContext(Dispatchers.IO) {
            val soap = contactsSoap(mailboxEmail)
            runCatching {
                val response = http.postSoap(endpoint, username, password, "http://schemas.microsoft.com/exchange/services/2006/messages/FindItem", soap, mailboxEmail)
                val contacts = parseContacts(response.body, mailboxId)
                val code = response.body.responseCode()
                EwsContactSyncResult(response.statusCode in 200..299 && code == "NoError", contacts, code.ifBlank { "HTTP " + response.statusCode })
            }.getOrElse { EwsContactSyncResult(false, emptyList(), it.localizedMessage ?: "Contact sync failed") }
        }

    private fun calendarSoap(email: String, start: String, end: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types" xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages">
          <soap:Header><t:RequestServerVersion Version="Exchange2016"/></soap:Header>
          <soap:Body><m:FindItem Traversal="Shallow">
            <m:ItemShape><t:BaseShape>IdOnly</t:BaseShape><t:AdditionalProperties>
              <t:FieldURI FieldURI="item:Subject"/><t:FieldURI FieldURI="item:Body"/><t:FieldURI FieldURI="calendar:Start"/><t:FieldURI FieldURI="calendar:End"/><t:FieldURI FieldURI="calendar:Location"/><t:FieldURI FieldURI="calendar:IsAllDayEvent"/>
            </t:AdditionalProperties></m:ItemShape>
            <m:CalendarView MaxEntriesReturned="100" StartDate="__START__" EndDate="__END__"/>
            <m:ParentFolderIds><t:DistinguishedFolderId Id="calendar"><t:Mailbox><t:EmailAddress>__EMAIL__</t:EmailAddress></t:Mailbox></t:DistinguishedFolderId></m:ParentFolderIds>
          </m:FindItem></soap:Body>
        </soap:Envelope>
    """.trimIndent().replace("__START__", start).replace("__END__", end).replace("__EMAIL__", escape(email))

    private fun contactsSoap(email: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types" xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages">
          <soap:Header><t:RequestServerVersion Version="Exchange2016"/></soap:Header>
          <soap:Body><m:FindItem Traversal="Shallow">
            <m:ItemShape><t:BaseShape>IdOnly</t:BaseShape><t:AdditionalProperties>
              <t:FieldURI FieldURI="contacts:DisplayName"/><t:FieldURI FieldURI="contacts:EmailAddress"/><t:FieldURI FieldURI="contacts:PhoneNumber"/><t:FieldURI FieldURI="contacts:Department"/><t:FieldURI FieldURI="contacts:CompanyName"/><t:FieldURI FieldURI="contacts:OfficeLocation"/>
            </t:AdditionalProperties></m:ItemShape>
            <m:IndexedPageItemView MaxEntriesReturned="100" Offset="0" BasePoint="Beginning"/>
            <m:ParentFolderIds><t:DistinguishedFolderId Id="contacts"><t:Mailbox><t:EmailAddress>__EMAIL__</t:EmailAddress></t:Mailbox></t:DistinguishedFolderId></m:ParentFolderIds>
          </m:FindItem></soap:Body>
        </soap:Envelope>
    """.trimIndent().replace("__EMAIL__", escape(email))

    private fun parseCalendar(xml: String, mailboxId: String): List<CalendarEventEntity> {
        val out = mutableListOf<CalendarEventEntity>()
        val p = parser(xml)
        var inside=false; var tag=""; var id=""; var subject=""; var body=""; var start=0L; var end=0L; var location=""; var allDay=false
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag=p.name
                    if (tag.equals("CalendarItem",true)) { inside=true; id=""; subject=""; body=""; start=0; end=0; location=""; allDay=false }
                    if (inside && tag.equals("ItemId",true)) id=p.getAttributeValue(null,"Id").orEmpty()
                }
                XmlPullParser.TEXT -> if (inside) {
                    val v=p.text.trim()
                    when { tag.equals("Subject",true)->subject=v; tag.equals("Body",true)->body+=v; tag.equals("Start",true)->start=parseTime(v); tag.equals("End",true)->end=parseTime(v); tag.equals("Location",true)->location=v; tag.equals("IsAllDayEvent",true)->allDay=v.equals("true",true) }
                }
                XmlPullParser.END_TAG -> if (p.name.equals("CalendarItem",true) && inside) {
                    if (id.isNotBlank() && start > 0) out += CalendarEventEntity(id,mailboxId,subject.ifBlank{"(No Subject)"},strip(body),start,if(end>0)end else start,"Europe/Berlin",location,"",allDay,"NONE")
                    inside=false
                }
            }
            p.next()
        }
        return out
    }

    private fun parseContacts(xml: String, mailboxId: String): List<ContactEntity> {
        val out=mutableListOf<ContactEntity>(); val p=parser(xml)
        var inside=false;var tag="";var id="";var name="";var email="";var phone="";var dept="";var company="";var room=""
        while(p.eventType!=XmlPullParser.END_DOCUMENT){
            when(p.eventType){
                XmlPullParser.START_TAG->{tag=p.name;if(tag.equals("Contact",true)){inside=true;id="";name="";email="";phone="";dept="";company="";room=""};if(inside&&tag.equals("ItemId",true))id=p.getAttributeValue(null,"Id").orEmpty()}
                XmlPullParser.TEXT->if(inside){val v=p.text.trim();when{tag.equals("DisplayName",true)->name=v;tag.equals("EmailAddress",true)&&email.isBlank()->email=v;tag.equals("PhoneNumber",true)&&phone.isBlank()->phone=v;tag.equals("Department",true)->dept=v;tag.equals("CompanyName",true)->company=v;tag.equals("OfficeLocation",true)->room=v}}
                XmlPullParser.END_TAG->if(p.name.equals("Contact",true)&&inside){if(id.isNotBlank()&&name.isNotBlank())out+=ContactEntity(id,mailboxId,name,email,phone,company,dept,room,false);inside=false}
            };p.next()
        }
        return out
    }

    private fun parser(xml:String): XmlPullParser = XmlPullParserFactory.newInstance().apply{isNamespaceAware=true}.newPullParser().also{it.setInput(StringReader(xml))}
    private fun parseTime(v:String):Long=runCatching{SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).apply{timeZone=TimeZone.getTimeZone("UTC")}.parse(v.substringBefore("."))?.time?:0L}.getOrDefault(0L)
    private fun utc(ms:Long):String=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US).apply{timeZone=TimeZone.getTimeZone("UTC")}.format(Date(ms))
    private fun strip(v:String)=v.replace(Regex("<[^>]*>")," ").replace(Regex("\\s+")," ").trim()
    private fun escape(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    private fun String.responseCode():String{var result="";runCatching{val p=parser(this);var tag="";while(p.eventType!=XmlPullParser.END_DOCUMENT){if(p.eventType==XmlPullParser.START_TAG)tag=p.name;if(p.eventType==XmlPullParser.TEXT&&tag.equals("ResponseCode",true)){result=p.text.trim();break};p.next()}};return result}
}

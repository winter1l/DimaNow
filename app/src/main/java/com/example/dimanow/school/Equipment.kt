package com.example.dimanow.school

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** DIMA C 기자재 대여신청 (D-107). The app reads it; renting stays on this page. */
const val DIMA_C_EQUIPMENT_URL = "https://udimac.dima.ac.kr/forward/ST/MP/RT/ST_MPRT01_L01.do?MENU_CD=03120101"

private const val FETCH_HELPERS = """
    async function dimaJson(url,body){
      var options={credentials:'same-origin',headers:{'X-Requested-With':'XMLHttpRequest'}};
      if(body!==undefined){
        options.method='POST';
        options.headers['Content-Type']='application/x-www-form-urlencoded; charset=UTF-8';
        options.body=body;
      }
      var response=await fetch(url,options);
      var text=await response.text();
      try{return JSON.parse(text);}catch(e){return {__text:text.slice(0,120)};}
    }
    function dimaSignedOut(value){return value&&value.__text!==undefined;}
"""

/**
 * Every rental group the site lists and every model in use, all pages of each (D-130). It used to keep only
 * `일반대여` groups, only models with a rentable unit and only the first page, so some equipment never showed.
 */
const val DIMA_C_EQUIPMENT_CATALOG_SCRIPT = FETCH_HELPERS + """
    var groups=await dimaJson('/ajax/ST_MPRT01_SVC/ST_MPRT01_R10.do');
    if(dimaSignedOut(groups)){
      return /로그인|세션/.test(groups.__text)?{state:'signin'}:{state:'error',message:'DIMA C 응답을 읽지 못했어요.'};
    }
    var out=[];
    var list=(groups.list||[]).filter(function(g){return g.USE_YN==='Y';});
    var size=100;
    for(var i=0;i<list.length;i++){
      var g=list[i];
      var seen={};
      var models=[];
      // Page by page until a short or repeated page; one request at a time with a short pause, as a person would.
      for(var page=1;page<=20;page++){
        if(i>0||page>1)await new Promise(function(resolve){setTimeout(resolve,300);});
        var answer=await dimaJson('/ajax/ST_MPRT01_SVC/ST_MPRT01_R11.do?pageNo='+page+'&MENU_CD=03120101&recordCountPerPage='+size+'&DP_GRP_NO='+encodeURIComponent(g.DP_GRP_NO));
        if(dimaSignedOut(answer))return {state:'signin'};
        var rows=answer.list||[];
        var fresh=rows.filter(function(m){return !seen[m.MODEL_NO];});
        fresh.forEach(function(m){seen[m.MODEL_NO]=true;models.push(m);});
        if(rows.length<size||fresh.length===0)break;
      }
      out.push({
        name:g.DP_GRP_NM,
        models:models.filter(function(m){return m.MODEL_USE_YN==='Y';})
          .map(function(m){return {no:m.MODEL_NO,name:(m.MODEL_NM||'').trim(),units:m.ITEM_CNT_ENABLE||0};})
      });
    }
    return {state:'ok',data:{groups:out}};
"""

/**
 * One model's rentable days, units and bookings. Only dates and unit numbers leave the page: the site's
 * answer also names other students and their reasons, which are dropped here (D-107).
 */
fun dimaCModelAvailabilityScript(modelNo: Long): String = FETCH_HELPERS + """
    var model='$modelNo';
    var calendar=await dimaJson('/ajax/CM_SVC/CM_getCalendar.do','DAYS_NUM=14&MODEL_NO='+model);
    if(dimaSignedOut(calendar))return /로그인|세션/.test(calendar.__text)?{state:'signin'}:{state:'error',message:'DIMA C 응답을 읽지 못했어요.'};
    var units=await dimaJson('/ajax/ST_MPRT01_SVC/ST_MPRT01_R03.do','MODEL_NO='+model);
    var rents=await dimaJson('/ajax/ST_MPRT01_SVC/ST_MPRT02_R00.do','MODEL_NO='+model+'&recordCountPerPage=10000');
    if(dimaSignedOut(units)||dimaSignedOut(rents))return {state:'signin'};
    return {state:'ok',data:{
      days:(calendar.list||[]).map(function(d){return {day:String(d.SDAY),weekday:String(d.YOIL),rentable:d.RENTABLE_YN==='Y',holiday:d.SUBJECT!=null};}),
      units:(Array.isArray(units)?units:[]).map(function(u){return String(u.ITEM_MNGT_NO);}),
      bookings:((rents&&rents.list)||[]).map(function(r){return {unit:String(r.ITEM_MNGT_NO),from:String(r.FROM_DT),to:String(r.TO_DT)};})
    }};
"""

data class EquipmentModel(val no: Long, val name: String, val units: Int)

data class EquipmentGroup(val name: String, val models: List<EquipmentModel>) {
    /** `카메라(일반대여)` → `카메라`. */
    val displayName: String get() = name.replace("(일반대여)", "").trim()
}

/**
 * One day for one model: [open] says whether DIMA C takes rentals that day; [freeUnits] counts units not booked;
 * [holiday] marks a day DIMA C's calendar names as a holiday.
 */
data class EquipmentDay(val date: LocalDate, val open: Boolean, val freeUnits: Int, val totalUnits: Int, val holiday: Boolean = false)

fun parseEquipmentCatalog(json: String): List<EquipmentGroup> =
    Json.parseToJsonElement(json).jsonObject["groups"]?.jsonArray.orEmpty().mapNotNull { element ->
        val group = element as? JsonObject ?: return@mapNotNull null
        val models = group["models"]?.jsonArray.orEmpty().mapNotNull { modelElement ->
            val model = modelElement as? JsonObject ?: return@mapNotNull null
            val no = model["no"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            EquipmentModel(no, model["name"]?.jsonPrimitive?.content.orEmpty(), model["units"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
        }
        EquipmentGroup(group["name"]?.jsonPrimitive?.content.orEmpty(), models).takeIf { models.isNotEmpty() }
    }

/**
 * The site's own rule (its booking page): a unit is busy on a day strictly inside one of its bookings, or on
 * a day that is both a return and a pickup day; on a lone pickup or return day it can still be taken.
 * Weekends, holidays and days outside the booking window are closed.
 */
fun parseEquipmentAvailability(json: String): List<EquipmentDay> {
    val root = Json.parseToJsonElement(json).jsonObject
    val units = root["units"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.distinct()
    val bookings = root["bookings"]?.jsonArray.orEmpty().mapNotNull { element ->
        val booking = element as? JsonObject ?: return@mapNotNull null
        val from = booking["from"]?.jsonPrimitive?.content?.toDayOrNull() ?: return@mapNotNull null
        val to = booking["to"]?.jsonPrimitive?.content?.toDayOrNull() ?: return@mapNotNull null
        Triple(booking["unit"]?.jsonPrimitive?.content.orEmpty(), from, to)
    }
    return root["days"]?.jsonArray.orEmpty().mapNotNull { element ->
        val day = element as? JsonObject ?: return@mapNotNull null
        val date = day["day"]?.jsonPrimitive?.content?.toDayOrNull() ?: return@mapNotNull null
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        val holiday = day["holiday"]?.jsonPrimitive?.booleanOrNull == true
        val open = day["rentable"]?.jsonPrimitive?.booleanOrNull == true && !holiday && !weekend
        val busy = units.count { unit ->
            val own = bookings.filter { it.first == unit }
            own.any { (_, from, to) -> date.isAfter(from) && date.isBefore(to) } ||
                (own.any { it.third == date } && own.any { it.second == date })
        }
        EquipmentDay(date, open, if (open) units.size - busy else 0, units.size, holiday)
    }
}

private fun String.toDayOrNull(): LocalDate? {
    val digits = filter(Char::isDigit)
    if (digits.length != 8) return null
    return runCatching { LocalDate.of(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt()) }.getOrNull()
}

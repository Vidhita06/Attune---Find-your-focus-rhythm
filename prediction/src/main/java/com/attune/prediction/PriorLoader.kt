package com.attune.prediction
import com.attune.prediction.model.RiskLevel
data class CategoryPrior(val category:String, val risk:RiskLevel, val defaultTimeAwayMin:Float, val defaultRecoveryMin:Float, val description:String)
object PriorLoader {
    private val default = CategoryPrior("unknown",RiskLevel.MED,8f,4f,"General starting estimate")
    fun loadFromJson(json:String): Map<String,CategoryPrior> = try { parse(json) } catch(e:Exception) { emptyMap() }
    fun getDefault() = default
    private fun parse(json:String): Map<String,CategoryPrior> {
        val r = mutableMapOf<String,CategoryPrior>()
        Regex(""""([\w_-]+)"\s*:\s*\{([^}]+)\}""").findAll(json).forEach { m ->
            val cat=m.groupValues[1]; if(cat=="metadata") return@forEach
            val b=m.groupValues[2]
            val risk=str(b,"risk")?.let { runCatching{RiskLevel.valueOf(it.uppercase())}.getOrNull() } ?: RiskLevel.MED
            r[cat]=CategoryPrior(cat,risk,flt(b,"timeAwayMin")?:8f,flt(b,"recoveryMin")?:4f,str(b,"description")?:"Starting estimate")
        }
        return r
    }
    private fun str(b:String,k:String)=Regex(""""$k"\s*:\s*"([^"]+)"""").find(b)?.groupValues?.get(1)
    private fun flt(b:String,k:String)=Regex(""""$k"\s*:\s*([\d.]+)""").find(b)?.groupValues?.get(1)?.toFloatOrNull()
}

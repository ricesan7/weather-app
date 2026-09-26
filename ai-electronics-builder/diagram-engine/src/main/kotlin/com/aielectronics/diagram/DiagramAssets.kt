package com.aielectronics.diagram

import com.aielectronics.core.model.*

data class DiagramAsset(
    val assetId: String,
    val key: String,
    val displayName: String,
    val kind: DiagramVisualKind,
    val size: VisualSize,
    val pinAnchors: Map<String, DiagramPinAnchor>,
)

data class DiagramPinAnchor(
    val label: String,
    val point: VisualPoint,
)

interface DiagramAssetCatalog {
    fun asset(key: String): DiagramAsset?
}

object GoldenDiagramAssetCatalog : DiagramAssetCatalog {
    private val assets = listOf(
        DiagramAsset(
            assetId = "asset_xiao_esp32s3_v1",
            key = "xiao_esp32s3",
            displayName = "XIAO ESP32S3",
            kind = DiagramVisualKind.MCU_BOARD,
            size = VisualSize(180.0, 240.0),
            pinAnchors = mapOf(
                "pin_xiao_d3_gpio4" to DiagramPinAnchor("D3 / GPIO4", VisualPoint(180.0, 72.0)),
                "pin_xiao_d4_gpio5" to DiagramPinAnchor("D4 / GPIO5 / SDA", VisualPoint(180.0, 108.0)),
                "pin_xiao_d5_gpio6" to DiagramPinAnchor("D5 / GPIO6 / SCL", VisualPoint(180.0, 144.0)),
                "pin_xiao_3v3" to DiagramPinAnchor("3V3", VisualPoint(0.0, 64.0)),
                "pin_xiao_gnd" to DiagramPinAnchor("GND", VisualPoint(0.0, 120.0)),
                "pin_xiao_5v" to DiagramPinAnchor("5V", VisualPoint(0.0, 176.0)),
            ),
        ),
        DiagramAsset(
            assetId = "asset_ae_sht31_v1",
            key = "ae_sht31",
            displayName = "AE-SHT31",
            kind = DiagramVisualKind.SENSOR_MODULE,
            size = VisualSize(170.0, 130.0),
            pinAnchors = mapOf(
                "cp_ae_sht31_1" to DiagramPinAnchor("VDD", VisualPoint(0.0, 24.0)),
                "cp_ae_sht31_2" to DiagramPinAnchor("SDA", VisualPoint(0.0, 46.0)),
                "cp_ae_sht31_3" to DiagramPinAnchor("SCL", VisualPoint(0.0, 68.0)),
                "cp_ae_sht31_4" to DiagramPinAnchor("ADR (OPEN)", VisualPoint(0.0, 90.0)),
                "cp_ae_sht31_5" to DiagramPinAnchor("GND", VisualPoint(0.0, 112.0)),
            ),
        ),
        DiagramAsset(
            assetId = "asset_adafruit_lps22_4633_v1",
            key = "adafruit_lps22_4633",
            displayName = "Adafruit LPS22",
            kind = DiagramVisualKind.SENSOR_MODULE,
            size = VisualSize(170.0, 130.0),
            pinAnchors = mapOf(
                "cp_lps22_vin" to DiagramPinAnchor("VIN", VisualPoint(0.0, 22.0)),
                "cp_lps22_gnd" to DiagramPinAnchor("GND", VisualPoint(0.0, 44.0)),
                "cp_lps22_sda" to DiagramPinAnchor("SDA", VisualPoint(0.0, 68.0)),
                "cp_lps22_scl" to DiagramPinAnchor("SCL", VisualPoint(0.0, 92.0)),
                "cp_lps22_sdo" to DiagramPinAnchor("SDO / ADDR", VisualPoint(0.0, 114.0)),
            ),
        ),
        DiagramAsset(
            assetId = "asset_adafruit_vcnl4030_6491_v1",
            key = "adafruit_vcnl4030_6491",
            displayName = "Adafruit VCNL4030",
            kind = DiagramVisualKind.SENSOR_MODULE,
            size = VisualSize(170.0, 120.0),
            pinAnchors = mapOf(
                "cp_vcnl4030_vin" to DiagramPinAnchor("VIN", VisualPoint(0.0, 22.0)),
                "cp_vcnl4030_gnd" to DiagramPinAnchor("GND", VisualPoint(0.0, 46.0)),
                "cp_vcnl4030_sda" to DiagramPinAnchor("SDA", VisualPoint(0.0, 72.0)),
                "cp_vcnl4030_scl" to DiagramPinAnchor("SCL", VisualPoint(0.0, 96.0)),
            ),
        ),
        DiagramAsset(
            assetId = "asset_tbd62003apg_v1",
            key = "tbd62003apg",
            displayName = "TBD62003APG",
            kind = DiagramVisualKind.DIP_IC,
            size = VisualSize(180.0, 190.0),
            pinAnchors = mapOf(
                "cp_tbd62003_1" to DiagramPinAnchor("I1 / pin1", VisualPoint(0.0, 34.0)),
                "cp_tbd62003_8" to DiagramPinAnchor("GND / pin8", VisualPoint(0.0, 156.0)),
                "cp_tbd62003_16" to DiagramPinAnchor("O1 / pin16", VisualPoint(180.0, 34.0)),
                "cp_tbd62003_9" to DiagramPinAnchor("COMMON / pin9", VisualPoint(180.0, 156.0)),
            ),
        ),
        DiagramAsset(
            assetId = "asset_ydm2510c05_v1",
            key = "fan_ydm2510c05",
            displayName = "YDM2510C05 5V FAN",
            kind = DiagramVisualKind.FAN,
            size = VisualSize(170.0, 170.0),
            pinAnchors = mapOf(
                "cp_fan_ydm2510_red" to DiagramPinAnchor("RED +5V", VisualPoint(0.0, 68.0)),
                "cp_fan_ydm2510_black" to DiagramPinAnchor("BLACK -", VisualPoint(0.0, 106.0)),
            ),
        ),
        DiagramAsset(
            assetId = "asset_ad_t50p200_v1",
            key = "psu_ad_t50p200_5v2a",
            displayName = "5V 2A POWER",
            kind = DiagramVisualKind.POWER_SUPPLY,
            size = VisualSize(200.0, 120.0),
            pinAnchors = mapOf(
                "positive" to DiagramPinAnchor("+5V", VisualPoint(200.0, 38.0)),
                "negative" to DiagramPinAnchor("GND", VisualPoint(200.0, 84.0)),
            ),
        ),
    ).associateBy { it.key }

    override fun asset(key: String): DiagramAsset? = assets[key]
}

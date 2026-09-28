package com.aielectronics.diagram

import com.aielectronics.core.model.*

class SvgDiagramRenderer {

    fun render(spec: DiagramSpec, viewId: String): String {
        val view = spec.views.firstOrNull { it.id == viewId }
            ?: error("Diagram view not found: " + viewId)
        val highlighted = view.highlightedConnectionIds.toSet()

        return buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
            append("width=\"").append(spec.canvasWidth).append("\" ")
            append("height=\"").append(spec.canvasHeight).append("\" ")
            append("viewBox=\"0 0 ").append(spec.canvasWidth).append(" ")
                .append(spec.canvasHeight).append("\">")
            append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>")
            append("<text x=\"28\" y=\"34\" font-size=\"22\" font-family=\"sans-serif\" font-weight=\"700\">")
            append(escape(view.title))
            append("</text>")

            spec.wires.forEach { wire ->
                drawWire(this, wire, highlighted)
            }

            spec.placements.forEach { placement ->
                drawPlacement(this, placement)
            }

            append("</svg>")
        }
    }

    private fun drawWire(
        out: StringBuilder,
        wire: DiagramWire,
        highlighted: Set<String>,
    ) {
        val isHighlighted = highlighted.isEmpty() || wire.connectionId in highlighted
        val opacity = if (isHighlighted) "1.0" else "0.16"
        val width = if (wire.connectionId in highlighted) "7" else "4"
        val color = wireColor(wire)

        out.append("<polyline data-connection-id=\"")
            .append(escape(wire.connectionId))
            .append("\" data-highlighted=\"")
            .append((wire.connectionId in highlighted).toString())
            .append("\" points=\"")
            .append(wire.points.joinToString(" ") { point ->
                point.x.toInt().toString() + "," + point.y.toInt()
            })
            .append("\" fill=\"none\" stroke=\"")
            .append(color)
            .append("\" stroke-width=\"")
            .append(width)
            .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\" opacity=\"")
            .append(opacity)
            .append("\"/>")
    }

    private fun drawPlacement(
        out: StringBuilder,
        p: DiagramPlacement,
    ) {
        out.append("<g data-entity-id=\"").append(escape(p.entityId)).append("\">")
        when (p.kind) {
            DiagramVisualKind.MCU_BOARD -> drawBoard(out, p)
            DiagramVisualKind.SENSOR_MODULE -> drawSensor(out, p)
            DiagramVisualKind.DIP_IC -> drawDip(out, p)
            DiagramVisualKind.FAN -> drawFan(out, p)
            DiagramVisualKind.POWER_SUPPLY -> drawPower(out, p)
            DiagramVisualKind.GENERIC -> drawGeneric(out, p)
        }

        out.append("<text x=\"").append((p.origin.x + p.size.width / 2).toInt())
            .append("\" y=\"").append((p.origin.y - 10).toInt())
            .append("\" text-anchor=\"middle\" font-family=\"sans-serif\" font-size=\"14\" font-weight=\"700\">")
            .append(escape(p.label))
            .append("</text>")

        p.pins.forEach { pin ->
            out.append("<circle cx=\"").append(pin.point.x.toInt())
                .append("\" cy=\"").append(pin.point.y.toInt())
                .append("\" r=\"5\" fill=\"#ffffff\" stroke=\"#222222\" stroke-width=\"2\"/>")
            val textX = if (pin.point.x <= p.origin.x + 1.0) pin.point.x + 8 else pin.point.x - 8
            val anchor = if (pin.point.x <= p.origin.x + 1.0) "start" else "end"
            out.append("<text x=\"").append(textX.toInt())
                .append("\" y=\"").append((pin.point.y - 7).toInt())
                .append("\" text-anchor=\"").append(anchor)
                .append("\" font-family=\"sans-serif\" font-size=\"10\">")
                .append(escape(pin.label))
                .append("</text>")
        }
        out.append("</g>")
    }

    private fun drawBoard(out: StringBuilder, p: DiagramPlacement) {
        rect(out, p, "#1f7a55", 18.0)
        val usbX = p.origin.x + p.size.width / 2 - 32
        out.append("<rect x=\"").append(usbX.toInt())
            .append("\" y=\"").append(p.origin.y.toInt())
            .append("\" width=\"64\" height=\"26\" rx=\"4\" fill=\"#d9d9d9\"/>")
        out.append("<rect x=\"").append((p.origin.x + 58).toInt())
            .append("\" y=\"").append((p.origin.y + 78).toInt())
            .append("\" width=\"64\" height=\"72\" rx=\"6\" fill=\"#303030\"/>")
    }

    private fun drawSensor(out: StringBuilder, p: DiagramPlacement) {
        rect(out, p, "#3b8f75", 12.0)
        out.append("<rect x=\"").append((p.origin.x + 72).toInt())
            .append("\" y=\"").append((p.origin.y + 40).toInt())
            .append("\" width=\"52\" height=\"52\" rx=\"5\" fill=\"#d7d7d7\" stroke=\"#555\"/>")
    }

    private fun drawDip(out: StringBuilder, p: DiagramPlacement) {
        rect(out, p, "#333333", 10.0)
        out.append("<circle cx=\"").append((p.origin.x + p.size.width / 2).toInt())
            .append("\" cy=\"").append((p.origin.y + 14).toInt())
            .append("\" r=\"6\" fill=\"#aaaaaa\"/>")
    }

    private fun drawFan(out: StringBuilder, p: DiagramPlacement) {
        val cx = p.origin.x + p.size.width / 2
        val cy = p.origin.y + p.size.height / 2
        out.append("<rect x=\"").append(p.origin.x.toInt())
            .append("\" y=\"").append(p.origin.y.toInt())
            .append("\" width=\"").append(p.size.width.toInt())
            .append("\" height=\"").append(p.size.height.toInt())
            .append("\" rx=\"14\" fill=\"#ececec\" stroke=\"#333\" stroke-width=\"3\"/>")
        out.append("<circle cx=\"").append(cx.toInt())
            .append("\" cy=\"").append(cy.toInt())
            .append("\" r=\"62\" fill=\"#2f2f2f\"/>")
        for (i in 0 until 4) {
            val angle = i * 90
            out.append("<ellipse cx=\"").append(cx.toInt())
                .append("\" cy=\"").append((cy - 32).toInt())
                .append("\" rx=\"18\" ry=\"42\" fill=\"#777\" transform=\"rotate(")
                .append(angle).append(" ").append(cx.toInt()).append(" ").append(cy.toInt())
                .append(")\"/>")
        }
        out.append("<circle cx=\"").append(cx.toInt())
            .append("\" cy=\"").append(cy.toInt())
            .append("\" r=\"18\" fill=\"#aaaaaa\"/>")
    }

    private fun drawPower(out: StringBuilder, p: DiagramPlacement) {
        rect(out, p, "#444444", 14.0)
        out.append("<text x=\"").append((p.origin.x + p.size.width / 2).toInt())
            .append("\" y=\"").append((p.origin.y + 68).toInt())
            .append("\" text-anchor=\"middle\" fill=\"#ffffff\" font-family=\"sans-serif\" font-size=\"20\" font-weight=\"700\">5V 2A</text>")
    }

    private fun drawGeneric(out: StringBuilder, p: DiagramPlacement) {
        rect(out, p, "#dddddd", 10.0)
    }

    private fun rect(out: StringBuilder, p: DiagramPlacement, fill: String, radius: Double) {
        out.append("<rect x=\"").append(p.origin.x.toInt())
            .append("\" y=\"").append(p.origin.y.toInt())
            .append("\" width=\"").append(p.size.width.toInt())
            .append("\" height=\"").append(p.size.height.toInt())
            .append("\" rx=\"").append(radius.toInt())
            .append("\" fill=\"").append(fill)
            .append("\" stroke=\"#222222\" stroke-width=\"3\"/>")
    }

    private fun wireColor(wire: DiagramWire): String =
        when {
            wire.netType == NetType.GROUND -> "#222222"
            wire.netType == NetType.POWER -> "#e53935"
            wire.netType == NetType.I2C_SDA -> "#2e7d32"
            wire.netType == NetType.I2C_SCL -> "#1565c0"
            wire.netType == NetType.CONTROL -> "#ef6c00"
            wire.netType == NetType.LOAD -> "#6a1b9a"
            else -> "#546e7a"
        }

    private fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}

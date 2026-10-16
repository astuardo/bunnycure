package cl.bunnycure.service.sii;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Lectura tolerante de las respuestas de la API de emisión de boletas (SII vía ApiGateway):
 * la misma información puede venir en distintas formas según el endpoint y la versión.
 */
public final class SiiResponseParser {

    private SiiResponseParser() {
    }

    public static JsonNode extractDocsArray(JsonNode siiResponse) {
        if (siiResponse == null) return null;
        if (siiResponse.isArray()) return siiResponse;
        if (siiResponse.has("documentos") && siiResponse.get("documentos").isArray()) {
            return siiResponse.get("documentos");
        }
        if (siiResponse.has("data") && siiResponse.get("data").isArray()) {
            return siiResponse.get("data");
        }
        return null;
    }

    public static String extractFolio(JsonNode json) {
        if (json == null)
            return "N/A";
        if (json.has("folio"))
            return json.get("folio").asText();
        if (json.has("invoiceNumber"))
            return json.get("invoiceNumber").asText();
        if (json.has("boleta") && json.get("boleta").has("folio"))
            return json.get("boleta").get("folio").asText();
        if (json.has("Encabezado") && json.get("Encabezado").has("IdDoc")
                && json.get("Encabezado").get("IdDoc").has("Folio")) {
            return json.get("Encabezado").get("IdDoc").get("Folio").asText();
        }
        if (json.has("data")) {
            JsonNode data = json.get("data");
            if (data.has("folio"))
                return data.get("folio").asText();
            if (data.has("Encabezado") && data.get("Encabezado").has("IdDoc")
                    && data.get("Encabezado").get("IdDoc").has("Folio")) {
                return data.get("Encabezado").get("IdDoc").get("Folio").asText();
            }
            if (data.has("boleta") && data.get("boleta").has("folio")) {
                return data.get("boleta").get("folio").asText();
            }
        }
        return "N/A";
    }

    public static String extractCodigo(JsonNode json) {
        if (json == null)
            return null;
        if (json.has("codigo") && !json.get("codigo").isNull() && !json.get("codigo").asText().isBlank())
            return json.get("codigo").asText().trim();
        if (json.has("data") && json.get("data").has("codigo") && !json.get("data").get("codigo").isNull() && !json.get("data").get("codigo").asText().isBlank())
            return json.get("data").get("codigo").asText().trim();
        if (json.has("boleta") && json.get("boleta").has("codigo") && !json.get("boleta").get("codigo").isNull() && !json.get("boleta").get("codigo").asText().isBlank())
            return json.get("boleta").get("codigo").asText().trim();

        // 1. Extraer CodigoBarras desde IdDoc (es el identificador oficial del SII para el PDF: e.g. 1866971000066525C5FC)
        if (json.has("Encabezado") && json.get("Encabezado").has("IdDoc")) {
            JsonNode idDoc = json.get("Encabezado").get("IdDoc");
            if (idDoc.has("CodigoBarras") && !idDoc.get("CodigoBarras").isNull() && !idDoc.get("CodigoBarras").asText().isBlank())
                return idDoc.get("CodigoBarras").asText().trim();
            if (idDoc.has("Codigo") && !idDoc.get("Codigo").isNull() && !idDoc.get("Codigo").asText().isBlank())
                return idDoc.get("Codigo").asText().trim();
            if (idDoc.has("codigo") && !idDoc.get("codigo").isNull() && !idDoc.get("codigo").asText().isBlank())
                return idDoc.get("codigo").asText().trim();
        }
        if (json.has("data") && json.get("data").has("Encabezado") && json.get("data").get("Encabezado").has("IdDoc")) {
            JsonNode idDoc = json.get("data").get("Encabezado").get("IdDoc");
            if (idDoc.has("CodigoBarras") && !idDoc.get("CodigoBarras").isNull() && !idDoc.get("CodigoBarras").asText().isBlank())
                return idDoc.get("CodigoBarras").asText().trim();
            if (idDoc.has("Codigo") && !idDoc.get("Codigo").isNull() && !idDoc.get("Codigo").asText().isBlank())
                return idDoc.get("Codigo").asText().trim();
            if (idDoc.has("codigo") && !idDoc.get("codigo").isNull() && !idDoc.get("codigo").asText().isBlank())
                return idDoc.get("codigo").asText().trim();
        }
        if (json.has("codigo_barras") && !json.get("codigo_barras").isNull() && !json.get("codigo_barras").asText().isBlank())
            return json.get("codigo_barras").asText().trim();
        if (json.has("data") && json.get("data").has("codigo_barras") && !json.get("data").get("codigo_barras").isNull() && !json.get("data").get("codigo_barras").asText().isBlank())
            return json.get("data").get("codigo_barras").asText().trim();
        if (json.has("codigo_verificacion") && !json.get("codigo_verificacion").isNull() && !json.get("codigo_verificacion").asText().isBlank())
            return json.get("codigo_verificacion").asText().trim();
        if (json.has("codigoVerificacion") && !json.get("codigoVerificacion").isNull() && !json.get("codigoVerificacion").asText().isBlank())
            return json.get("codigoVerificacion").asText().trim();

        return null;
    }

    public static String extractBarcode(JsonNode json) {
        if (json == null)
            return null;
        if (json.has("codigo_barras") && !json.get("codigo_barras").isNull() && !json.get("codigo_barras").asText().isBlank())
            return json.get("codigo_barras").asText().trim();
        if (json.has("codigo_inferior") && !json.get("codigo_inferior").isNull() && !json.get("codigo_inferior").asText().isBlank())
            return json.get("codigo_inferior").asText().trim();
        if (json.has("boleta") && json.get("boleta").has("codigo_barras") && !json.get("boleta").get("codigo_barras").isNull())
            return json.get("boleta").get("codigo_barras").asText().trim();
        if (json.has("Encabezado") && json.get("Encabezado").has("IdDoc")) {
            JsonNode idDoc = json.get("Encabezado").get("IdDoc");
            if (idDoc.has("CodigoBarras") && !idDoc.get("CodigoBarras").isNull() && !idDoc.get("CodigoBarras").asText().isBlank())
                return idDoc.get("CodigoBarras").asText().trim();
            if (idDoc.has("CodigoInferior") && !idDoc.get("CodigoInferior").isNull() && !idDoc.get("CodigoInferior").asText().isBlank())
                return idDoc.get("CodigoInferior").asText().trim();
        }
        if (json.has("data")) {
            JsonNode data = json.get("data");
            if (data.has("codigo_barras") && !data.get("codigo_barras").isNull() && !data.get("codigo_barras").asText().isBlank())
                return data.get("codigo_barras").asText().trim();
            if (data.has("codigo_inferior") && !data.get("codigo_inferior").isNull() && !data.get("codigo_inferior").asText().isBlank())
                return data.get("codigo_inferior").asText().trim();
            if (data.has("Encabezado") && data.get("Encabezado").has("IdDoc")) {
                JsonNode idDoc = data.get("Encabezado").get("IdDoc");
                if (idDoc.has("CodigoBarras") && !idDoc.get("CodigoBarras").isNull() && !idDoc.get("CodigoBarras").asText().isBlank())
                    return idDoc.get("CodigoBarras").asText().trim();
                if (idDoc.has("CodigoInferior") && !idDoc.get("CodigoInferior").isNull() && !idDoc.get("CodigoInferior").asText().isBlank())
                    return idDoc.get("CodigoInferior").asText().trim();
            }
        }
        return null;
    }
}

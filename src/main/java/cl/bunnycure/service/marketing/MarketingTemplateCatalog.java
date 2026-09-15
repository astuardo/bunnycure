package cl.bunnycure.service.marketing;

import cl.bunnycure.web.dto.marketing.MarketingTemplateDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo centralizado de plantillas de marketing estacionales para BunnyCure en Chile.
 * Contiene tanto los metadatos de visualización como el payload requerido por Meta Graph API v22.0.
 */
@Component
public class MarketingTemplateCatalog {

    public record TemplateDefinition(
            String name,
            String displayName,
            String occasion,
            String emoji,
            String category,
            String language,
            String headerText,
            String bodyText,
            String footerText,
            String buttonText,
            String buttonUrl,
            List<String> sampleVariables
    ) {
        public MarketingTemplateDto toDto(String metaStatus, String metaId) {
            return MarketingTemplateDto.builder()
                    .name(name)
                    .displayName(displayName)
                    .occasion(occasion)
                    .emoji(emoji)
                    .category(category)
                    .language(language)
                    .metaStatus(metaStatus != null ? metaStatus : "NOT_REGISTERED")
                    .metaId(metaId)
                    .headerText(headerText)
                    .bodyText(bodyText)
                    .footerText(footerText)
                    .buttonText(buttonText)
                    .buttonUrl(buttonUrl)
                    .sampleVariables(sampleVariables)
                    .build();
        }

        public Map<String, Object> toMetaPayload() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("category", category);
            payload.put("language", language);

            List<Map<String, Object>> components = new ArrayList<>();

            if (headerText != null && !headerText.isBlank()) {
                components.add(Map.of(
                        "type", "HEADER",
                        "format", "TEXT",
                        "text", headerText
                ));
            }

            Map<String, Object> bodyComponent = new HashMap<>();
            bodyComponent.put("type", "BODY");
            bodyComponent.put("text", bodyText);
            if (sampleVariables != null && !sampleVariables.isEmpty()) {
                bodyComponent.put("example", Map.of("body_text", List.of(sampleVariables)));
            }
            components.add(bodyComponent);

            if (footerText != null && !footerText.isBlank()) {
                components.add(Map.of(
                        "type", "FOOTER",
                        "text", footerText
                ));
            }

            if (buttonText != null && !buttonText.isBlank() && buttonUrl != null && !buttonUrl.isBlank()) {
                components.add(Map.of(
                        "type", "BUTTONS",
                        "buttons", List.of(Map.of(
                                "type", "URL",
                                "text", buttonText,
                                "url", buttonUrl
                        ))
                ));
            }

            payload.put("components", components);
            return payload;
        }
    }

    private final List<TemplateDefinition> templates = List.of(
            new TemplateDefinition(
                    "promo_fiestas_patrias",
                    "Especial Fiestas Patrias 🇨🇱",
                    "Fiestas Patrias (18 y 19 de Septiembre)",
                    "🇨🇱",
                    "MARKETING",
                    "es_CL",
                    "Celebra el 18 con BunnyCure",
                    "¡Hola {{1}}! 🇨🇱💃✨\n\nYa se acerca el 18 y en BunnyCure sabemos que tus uñitas no pueden quedarse atrás 💅🍷\n\nTenemos diseños dieciocheros exclusivos, esmaltados tricolor y manicura rusa para que luzcas impecable estas fiestas.\n\n⚠️ Los cupos de la semana dieciochera son muy limitados y se llenan rápido.\n\n¿Aseguramos tu cita desde ya?",
                    "BunnyCure Studio",
                    "Reservar mi hora",
                    "https://reservar.bunnycure.cl",
                    List.of("Camila")
            ),
            new TemplateDefinition(
                    "promo_bienvenida_primavera",
                    "Bienvenida Primavera 🌸",
                    "Inicio de Primavera (21 de Septiembre)",
                    "🌸",
                    "MARKETING",
                    "es_CL",
                    "Bienvenida Primavera en BunnyCure",
                    "¡Hola {{1}}! 🌸🌷✨\n\nLlegó la primavera y con ella la temporada perfecta para llenar de color y frescura tus manos 💅\n\nEstrenamos nueva paleta de tonos pasteles, diseños florales y tratamientos de hidratación profunda para consentirte.\n\n¿Revisamos los horarios disponibles de esta semana?",
                    "BunnyCure Studio",
                    "Ver Horarios",
                    "https://reservar.bunnycure.cl",
                    List.of("Camila")
            ),
            new TemplateDefinition(
                    "promo_dia_de_la_madre",
                    "Día de la Madre 💐",
                    "Día de la Madre (Mayo)",
                    "💐",
                    "MARKETING",
                    "es_CL",
                    "Un regaloneo especial para Mama",
                    "¡Hola {{1}}! 💐💖\n\nSe acerca el Día de la Madre y en BunnyCure queremos ayudarte a sorprenderla con el mejor regalo: relajo, belleza y desconexión total 💅✨\n\nPuedes agendar una cita doble para venir juntas o regalarle una de nuestras Gift Cards digitales personalizadas 🎁\n\n¿Te gustaría coordinar una sorpresa especial?",
                    "BunnyCure Studio",
                    "Agendar Cita",
                    "https://reservar.bunnycure.cl",
                    List.of("Camila")
            ),
            new TemplateDefinition(
                    "promo_navidad_bunnycure",
                    "Navidad BunnyCure 🎄",
                    "Navidad (Diciembre)",
                    "🎄",
                    "MARKETING",
                    "es_CL",
                    "Brilla esta Navidad con BunnyCure",
                    "¡Hola {{1}}! 🎄✨🎅\n\nSe acercan las fiestas de fin de año, cenas y celebraciones, y sabemos que quieres lucir unas uñas espectaculares 💅❤️\n\nYa abrimos la agenda navideña con esmaltados glitter, efecto velvet y diseños elegantes para Nochebuena.\n\n⏳ Recuerda que diciembre es nuestro mes de mayor demanda y las horas vuelan.\n\n¡Reserva tu espacio con tiempo!",
                    "BunnyCure Studio",
                    "Asegurar mi Cita",
                    "https://reservar.bunnycure.cl",
                    List.of("Camila")
            ),
            new TemplateDefinition(
                    "promo_ano_nuevo_bunnycure",
                    "Año Nuevo Radiante 🍾",
                    "Año Nuevo (31 de Diciembre)",
                    "🍾",
                    "MARKETING",
                    "es_CL",
                    "Recibe el Ano Nuevo impecable",
                    "¡Hola {{1}}! 🍾🥂✨\n\n¿Lista para despedir el año y recibir el nuevo año con tus manos radiantes? 💅💫\n\nTenemos los últimos cupos disponibles para antes del 31 de diciembre. Tonos dorados, plateados, milky white y nail art festivo para empezar el año con la mejor energía.\n\n¡Agenda tu cita de Año Nuevo hoy mismo!",
                    "BunnyCure Studio",
                    "Agendar Ano Nuevo",
                    "https://reservar.bunnycure.cl",
                    List.of("Camila")
            ),
            new TemplateDefinition(
                    "promo_san_valentin",
                    "San Valentín / Día del Amor 💖",
                    "San Valentín (14 de Febrero)",
                    "💖",
                    "MARKETING",
                    "es_CL",
                    "Especial San Valentin en BunnyCure",
                    "¡Hola {{1}}! 💖🌹✨\n\nSe acerca el Día del Amor y la Amistad, y tus manos merecen lucir radiantes 💅💕\n\nTenemos diseños románticos exclusivos, corazones sutiles y tonos rojos elegantes para celebrar esta fecha especial.\n\n¡Reserva tu cita con anticipación y luce unas uñas irresistibles!",
                    "BunnyCure Studio",
                    "Reservar Cita",
                    "https://reservar.bunnycure.cl",
                    List.of("Camila")
            ),
            new TemplateDefinition(
                    "bunnycure_reactivacion_clienta",
                    "Reactivación de Clienta 🔄",
                    "Reactivación (Todo el año)",
                    "🔄",
                    "MARKETING",
                    "es_CL",
                    "¡Te extrañamos en BunnyCure!",
                    "¡Hola {{1}}! 🌸\n\nYa pasaron unas semanas desde tu último servicio de {{2}} en BunnyCure. Sabemos lo importante que es para ti mantener tus uñitas sanas y con un cuidado impecable ✨\n\nEstás justo a tiempo para tu mantención o para renovar tu diseño favorito 💅💖\n\n¿Te gustaría que te reservemos un espacio esta semana? Respóndenos y con gusto coordinamos tu cita 🥰",
                    "BunnyCure - Cuidado de uñas profesional",
                    "Escribir a BunnyCure",
                    "https://www.instagram.com/bunny.cure",
                    List.of("Camila", "Esmaltado Permanente")
            ),
            new TemplateDefinition(
                    "promo_halloween_bunnycure",
                    "Especial Halloween 🎃👻",
                    "Halloween (31 de Octubre)",
                    "🎃",
                    "MARKETING",
                    "es_CL",
                    "¡Halloween de Terror y Belleza en BunnyCure! 🎃",
                    "¡Hola {{1}}! 🎃👻✨\n\n¿Lista para impactar en esta noche de brujas? En BunnyCure ya tenemos disponibles nuestros diseños temáticos más pedidos 💅🕷️\n\nUñas con nail art de fantasmitas, calabazas, efectos velvet oscuros, glow in the dark y sangre glam para lucir una manicura de ensueño.\n\n⚠️ ¡La agenda para la semana de Halloween ya está abierta y los cupos vuelan!\n\n¿Aseguramos tu cita antes de que se agoten?",
                    "BunnyCure Studio",
                    "Reservar mi cita",
                    "https://reservar.bunnycure.cl",
                    List.of("Camila")
            ),
            new TemplateDefinition(
                    "saludo_cumpleanos_bunnycure",
                    "Especial Cumpleaños 🎂",
                    "Cumpleaños (Todo el año)",
                    "🎂",
                    "MARKETING",
                    "es_CL",
                    "Feliz Cumpleanos te desea BunnyCure",
                    "¡Hola {{1}}! 🎂✨ En BunnyCure te deseamos un muy feliz cumpleaños. Queremos regalonearte en tu día especial con {{2}}. 💅💖 ¿Te gustaría reservar tu cita para celebrarlo con nosotras?",
                    "BunnyCure Studio",
                    "Reservar mi cita",
                    "https://reservar.bunnycure.cl",
                    List.of("Camila", "un 15% de descuento exclusivo en tu próxima cita")
            )
    );

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private cl.bunnycure.domain.repository.MarketingTemplateRepository marketingTemplateRepository;

    public MarketingTemplateCatalog() {
    }

    public MarketingTemplateCatalog(cl.bunnycure.domain.repository.MarketingTemplateRepository marketingTemplateRepository) {
        this.marketingTemplateRepository = marketingTemplateRepository;
    }

    public List<TemplateDefinition> getAllDefinitions() {
        List<TemplateDefinition> all = new ArrayList<>(templates);
        if (marketingTemplateRepository != null) {
            try {
                List<cl.bunnycure.domain.model.MarketingTemplateEntity> dbTemplates = marketingTemplateRepository.findAllByOrderByCreatedAtDesc();
                for (cl.bunnycure.domain.model.MarketingTemplateEntity entity : dbTemplates) {
                    if (all.stream().noneMatch(t -> t.name().equalsIgnoreCase(entity.getName()))) {
                        all.add(toDefinition(entity));
                    }
                }
            } catch (Exception ex) {
                // Ignorar en entornos de test o inicialización temprana
            }
        }
        return all;
    }

    public Optional<TemplateDefinition> findByName(String name) {
        if (marketingTemplateRepository != null) {
            try {
                Optional<cl.bunnycure.domain.model.MarketingTemplateEntity> optDb = marketingTemplateRepository.findByNameIgnoreCase(name);
                if (optDb.isPresent()) {
                    return Optional.of(toDefinition(optDb.get()));
                }
            } catch (Exception ex) {
                // Fallback a plantillas estáticas
            }
        }
        return templates.stream()
                .filter(t -> t.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public static TemplateDefinition toDefinition(cl.bunnycure.domain.model.MarketingTemplateEntity entity) {
        List<String> sampleVars = List.of("Camila");
        if (entity.getSampleVariables() != null && !entity.getSampleVariables().isBlank()) {
            sampleVars = java.util.Arrays.stream(entity.getSampleVariables().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return new TemplateDefinition(
                entity.getName(),
                entity.getDisplayName(),
                entity.getOccasion() != null ? entity.getOccasion() : "General",
                entity.getEmoji() != null ? entity.getEmoji() : "✨",
                entity.getCategory() != null ? entity.getCategory() : "MARKETING",
                entity.getLanguage() != null ? entity.getLanguage() : "es_CL",
                entity.getHeaderText(),
                entity.getBodyText(),
                entity.getFooterText(),
                entity.getButtonText(),
                entity.getButtonUrl(),
                sampleVars
        );
    }
}

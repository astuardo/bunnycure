package cl.bunnycure.web.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST para manejar peticiones de metadatos y recursos especiales
 * como las de Chrome DevTools, para evitar errores 404 en los logs.
 */
@RestController
@RequestMapping("/.well-known")
public class WellKnownController {

    /**
     * Chrome DevTools busca este archivo para configuraciones específicas de la app.
     * Respondemos con un JSON vacío válido para evitar errores 404.
     */
    @GetMapping(value = "/appspecific/com.chrome.devtools.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> chromeDevTools() {
        Map<String, Object> response = new HashMap<>();
        return ResponseEntity.ok(response);
    }
}

package com.eventparser.controller;

import javax.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * Контроллер для обработки ошибок в приложении.
 * Предоставляет пользовательский интерфейс для отображения ошибок вместо стандартной страницы Whitelabel Error.
 */
@Controller
public class CustomErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public CustomErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    /**
     * Обрабатывает все запросы к /error и отображает пользовательскую страницу ошибки.
     *
     * @param request HTTP запрос
     * @param model Модель для передачи данных в представление
     * @return Имя шаблона для отображения
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        WebRequest webRequest = new ServletWebRequest(request);
        Map<String, Object> errorMap = errorAttributes.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults());

        HttpStatus status = getStatus(request);

        model.addAttribute("status", status.value());
        model.addAttribute("error", errorMap.get("error"));
        model.addAttribute("message", errorMap.get("message"));
        model.addAttribute("path", errorMap.get("path"));
        model.addAttribute("timestamp", errorMap.get("timestamp"));
        model.addAttribute("title", "Ошибка " + status.value());
        model.addAttribute("content", "error");

        return "th_layout";
    }

    /**
     * Получает статус ошибки из запроса.
     *
     * @param request HTTP запрос
     * @return Статус ошибки
     */
    private HttpStatus getStatus(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute("javax.servlet.error.status_code");
        if (statusCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        try {
            return HttpStatus.valueOf(statusCode);
        } catch (Exception ex) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}

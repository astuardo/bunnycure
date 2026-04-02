/**
 * Validación client-side para formulario de servicios
 */
(function() {
    'use strict';

    document.addEventListener('DOMContentLoaded', function() {
        const form = document.querySelector('form[novalidate]');
        if (!form) return;

        const nameInput = form.querySelector('input[name="name"]');
        const durationInput = form.querySelector('input[name="durationMinutes"]');
        const priceInput = form.querySelector('input[name="price"]');
        const displayOrderInput = form.querySelector('input[name="displayOrder"]');
        const descriptionInput = form.querySelector('input[name="description"]');

        // Inicializar contador de caracteres al cargar
        if (descriptionInput) {
            updateCharCounter();
            descriptionInput.addEventListener('input', updateCharCounter);
        }

        // Auto-corrección de duración al perder el foco
        if (durationInput) {
            durationInput.addEventListener('blur', function() {
                let value = parseInt(this.value);
                if (!isNaN(value) && value > 0) {
                    const remainder = value % 15;
                    if (remainder !== 0) {
                        value = Math.round(value / 15) * 15;
                        this.value = Math.max(15, Math.min(480, value));
                    }
                }
            });
            durationInput.addEventListener('change', validatePricePerMinute);
        }

        // Auto-redondeo de precio
        if (priceInput) {
            priceInput.addEventListener('blur', function() {
                const value = parseFloat(this.value);
                if (!isNaN(value)) {
                    this.value = Math.round(value);
                }
            });
            priceInput.addEventListener('change', validatePricePerMinute);
        }

        // Validación al enviar el formulario
        form.addEventListener('submit', function(event) {
            let isValid = true;

            if (!validateName()) isValid = false;
            if (!validateDuration()) isValid = false;
            if (!validatePrice()) isValid = false;
            if (!validateDisplayOrder()) isValid = false;
            if (!validateDescription()) isValid = false;

            if (!isValid) {
                event.preventDefault();
                event.stopPropagation();
                
                const firstError = form.querySelector('.is-invalid');
                if (firstError) {
                    firstError.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    firstError.focus();
                }
            }
        });

        function updateCharCounter() {
            const value = descriptionInput.value;
            const maxLength = 300;
            const charCounter = document.getElementById('descriptionCharCount');

            if (charCounter) {
                const length = value.length;
                const remaining = maxLength - length;
                charCounter.textContent = `${length}/${maxLength} caracteres`;
                
                // Cambiar color según cantidad restante
                charCounter.classList.remove('text-danger', 'text-warning', 'text-muted');
                if (remaining < 20) {
                    charCounter.classList.add('text-danger');
                } else if (remaining < 50) {
                    charCounter.classList.add('text-warning');
                } else {
                    charCounter.classList.add('text-muted');
                }
            }
        }

        function validateName() {
            if (!nameInput) return true;
            
            const value = nameInput.value.trim();
            const minLength = 3;
            const maxLength = 100;
            const pattern = /^[a-zA-ZáéíóúÁÉÍÓÚñÑ0-9\s+\-/().,]+$/;

            if (value === '') {
                showError(nameInput, 'El nombre es obligatorio');
                return false;
            }

            if (value.length < minLength) {
                showError(nameInput, `El nombre debe tener al menos ${minLength} caracteres`);
                return false;
            }

            if (value.length > maxLength) {
                showError(nameInput, `El nombre no puede superar ${maxLength} caracteres`);
                return false;
            }

            if (!pattern.test(value)) {
                showError(nameInput, 'El nombre contiene caracteres no permitidos');
                return false;
            }

            clearError(nameInput);
            return true;
        }

        function validateDuration() {
            if (!durationInput) return true;
            
            const value = parseInt(durationInput.value);
            const min = 15;
            const max = 480;

            if (isNaN(value) || durationInput.value.trim() === '') {
                showError(durationInput, 'La duración es obligatoria');
                return false;
            }

            if (value < min) {
                showError(durationInput, `La duración mínima es ${min} minutos`);
                return false;
            }

            if (value > max) {
                showError(durationInput, `La duración máxima es ${max} minutos (8 horas)`);
                return false;
            }

            if (value % 15 !== 0) {
                showError(durationInput, 'La duración debe ser múltiplo de 15 minutos');
                return false;
            }

            clearError(durationInput);
            return true;
        }

        function validatePrice() {
            if (!priceInput) return true;
            
            const value = parseFloat(priceInput.value);
            const min = 0.01;
            const max = 9999999.99;

            if (isNaN(value) || priceInput.value.trim() === '') {
                showError(priceInput, 'El precio es obligatorio');
                return false;
            }

            if (value < min) {
                showError(priceInput, `El precio mínimo es $${min}`);
                return false;
            }

            if (value > max) {
                showError(priceInput, `El precio máximo es $${max.toLocaleString('es-CL')}`);
                return false;
            }

            clearError(priceInput);
            return true;
        }

        function validateDisplayOrder() {
            if (!displayOrderInput) return true;
            
            const value = parseInt(displayOrderInput.value);
            const min = 0;
            const max = 9999;

            if (displayOrderInput.value.trim() === '') {
                clearError(displayOrderInput);
                return true;
            }

            if (isNaN(value)) {
                showError(displayOrderInput, 'El orden debe ser un número');
                return false;
            }

            if (value < min) {
                showError(displayOrderInput, 'El orden no puede ser negativo');
                return false;
            }

            if (value > max) {
                showError(displayOrderInput, `El orden no puede superar ${max}`);
                return false;
            }

            clearError(displayOrderInput);
            return true;
        }

        function validateDescription() {
            if (!descriptionInput) return true;
            
            const value = descriptionInput.value;
            const maxLength = 300;

            if (value.length > maxLength) {
                showError(descriptionInput, `La descripción no puede superar ${maxLength} caracteres`);
                return false;
            }

            clearError(descriptionInput);
            return true;
        }

        function validatePricePerMinute() {
            if (!priceInput || !durationInput) return true;
            
            const price = parseFloat(priceInput.value);
            const duration = parseInt(durationInput.value);
            const warningDiv = document.getElementById('priceWarning');

            if (isNaN(price) || isNaN(duration) || duration === 0) {
                if (warningDiv) warningDiv.remove();
                return true;
            }

            const pricePerMinute = price / duration;
            const threshold = 50;

            if (pricePerMinute < threshold) {
                if (!warningDiv) {
                    const warning = document.createElement('div');
                    warning.id = 'priceWarning';
                    warning.className = 'alert alert-warning mt-2 py-2';
                    warning.innerHTML = `
                        <i class="bi bi-exclamation-triangle me-2"></i>
                        <small>El precio parece bajo para esta duración (menos de $${threshold}/min). Verifique el valor.</small>
                    `;
                    priceInput.closest('.col-md-6').appendChild(warning);
                }
            } else {
                if (warningDiv) warningDiv.remove();
            }

            return true;
        }

        function showError(input, message) {
            input.classList.add('is-invalid');
            input.classList.remove('is-valid');
            
            let feedbackDiv = input.parentElement.querySelector('.invalid-feedback');
            if (!feedbackDiv) {
                const parent = input.closest('.input-group') || input.parentElement;
                feedbackDiv = parent.parentElement.querySelector('.invalid-feedback');
                
                if (!feedbackDiv) {
                    feedbackDiv = document.createElement('div');
                    feedbackDiv.className = 'invalid-feedback';
                    parent.parentElement.appendChild(feedbackDiv);
                }
            }
            feedbackDiv.textContent = message;
            feedbackDiv.style.display = 'block';
        }

        function clearError(input) {
            input.classList.remove('is-invalid');
            
            const feedbackDiv = input.closest('.col-12, .col-md-6, .col-md-8, .col-md-4')?.querySelector('.invalid-feedback');
            if (feedbackDiv && !feedbackDiv.hasAttribute('th:errors')) {
                feedbackDiv.textContent = '';
                feedbackDiv.style.display = 'none';
            }
        }
    });
})();
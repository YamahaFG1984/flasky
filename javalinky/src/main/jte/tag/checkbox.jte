@import com.example.javalinky.web.FormView

@param FormView form
@param String name
@param String label

<div class="form-check mb-3">
  <input class="form-check-input" type="checkbox" value="1"
         id="f-${name}" name="${name}"
         checked="${"1".equals(form.value(name))}">
  <label class="form-check-label" for="f-${name}">${label}</label>
</div>

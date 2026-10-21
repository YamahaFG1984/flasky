@import com.example.javalinky.web.FormView

@param FormView form
@param String name
@param String label
@param int rows = 4
@param String help = null

<div class="mb-3">
  <label class="form-label" for="f-${name}">${label}</label>
  <textarea class="form-control ${form.hasError(name) ? "is-invalid" : ""}"
            id="f-${name}" name="${name}" rows="${rows}">${form.value(name)}</textarea>
  @if(form.hasError(name))
    <div class="invalid-feedback">${form.error(name)}</div>
  @elseif(help != null)
    <div class="form-text">${help}</div>
  @endif
</div>

@import com.example.javalinky.web.FormView

@param FormView form
@param String name
@param String label
@param String type = "text"
@param String help = null
@param boolean required = false
@param String autocomplete = null

<div class="mb-3">
  <label class="form-label" for="f-${name}">
    ${label}@if(required)<span class="text-danger"> *</span>@endif
  </label>

  <input class="form-control ${form.hasError(name) ? "is-invalid" : ""}"
         id="f-${name}"
         name="${name}"
         type="${type}"
         value="${type.equals("password") ? "" : form.value(name)}"
         <%-- JTE 的“智能属性”：值是 null 或 false 时，整个属性不会出现在输出里。
              注意不能写成 @if(...)required@endif —— 属性名的位置不允许放 @if --%>
         autocomplete="${autocomplete}"
         required="${required}">

  @if(form.hasError(name))
    <div class="invalid-feedback">${form.error(name)}</div>
  @elseif(help != null)
    <div class="form-text">${help}</div>
  @endif
</div>

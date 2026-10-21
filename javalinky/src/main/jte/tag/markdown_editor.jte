@import com.example.javalinky.web.FormView

@param FormView form
@param String name
@param String label
@param String placeholder = "支持 Markdown：**粗体**、`代码`、- 列表、[链接](地址)"

<div class="mb-3">
  <label class="form-label" for="f-${name}">${label}</label>
  <textarea class="form-control ${form.hasError(name) ? "is-invalid" : ""}"
            id="f-${name}" name="${name}" rows="6"
            placeholder="${placeholder}"
            data-markdown-editor>${form.value(name)}</textarea>
  @if(form.hasError(name))
    <div class="invalid-feedback">${form.error(name)}</div>
  @endif
</div>

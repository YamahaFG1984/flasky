@import com.example.javalinky.web.*

@param Layout layout
@param FormView form
@param IndexPage page

@template.layout.base(layout = layout, content = @`
  <div class="pb-3 mb-4 border-bottom">
    <h1>Hello, ${page.name() == null ? "陌生人" : page.name()}!</h1>
    @if(page.name() != null)
      @if(page.known())
        <p class="text-secondary">又见面了！</p>
      @else
        <p class="text-secondary">很高兴认识你！</p>
      @endif
    @endif
  </div>

  <form method="post" action="/" novalidate class="col-md-6">
    @template.tag.csrf(token = layout.csrfToken())
    @template.tag.field(form = form, name = "name", label = "你叫什么名字？",
                        required = true)
    <button class="btn btn-primary" type="submit">提交</button>
  </form>
`)

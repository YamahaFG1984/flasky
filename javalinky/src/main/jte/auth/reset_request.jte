@import com.example.javalinky.web.FormView
@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes

@param Layout layout
@param FormView form

@template.layout.base(layout = layout, content = @`
  <div class="row justify-content-center">
    <div class="col-md-5">
      <h1 class="h3 mb-4">找回密码</h1>
      <p class="text-secondary">填入注册时用的邮箱，我们会把重置链接发过去。</p>

      <form method="post" action="${Routes.RESET_PASSWORD}" novalidate>
        @template.tag.csrf(token = layout.csrfToken())

        @template.tag.field(form = form, name = "email", label = "邮箱",
                            type = "email", required = true,
                            autocomplete = "email")

        <button class="btn btn-primary" type="submit">发送重置链接</button>
      </form>
    </div>
  </div>
`)

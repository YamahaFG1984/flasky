@import com.example.javalinky.web.FormView
@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes

@param Layout layout
@param FormView form

@template.layout.base(layout = layout, content = @`
  <div class="row justify-content-center">
    <div class="col-md-5">
      <h1 class="h3 mb-4">修改密码</h1>

      <form method="post" action="${Routes.CHANGE_PASSWORD}" novalidate>
        @template.tag.csrf(token = layout.csrfToken())

        @template.tag.field(form = form, name = "oldPassword", label = "当前密码",
                            type = "password", required = true,
                            autocomplete = "current-password")
        @template.tag.field(form = form, name = "password", label = "新密码",
                            type = "password", required = true,
                            help = "至少 8 位", autocomplete = "new-password")
        @template.tag.field(form = form, name = "password2", label = "再输入一次新密码",
                            type = "password", required = true,
                            autocomplete = "new-password")

        <button class="btn btn-primary" type="submit">更新密码</button>
      </form>
    </div>
  </div>
`)

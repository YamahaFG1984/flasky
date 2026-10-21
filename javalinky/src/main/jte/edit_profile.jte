@import com.example.javalinky.model.Role
@import com.example.javalinky.model.User
@import com.example.javalinky.web.*
@import java.util.List

@param Layout layout
@param FormView form
@param boolean admin
@param User targetUser = null
@param List<Role> roles = List.of()

@template.layout.base(layout = layout, content = @`
  <div class="col-lg-7">
    <h1 class="h3 mb-4">
      @if(admin)
        编辑 ${targetUser.getUsername()} 的资料
        <span class="badge text-bg-danger align-middle">管理员</span>
      @else
        编辑我的资料
      @endif
    </h1>

    <form method="post" novalidate>
      @template.tag.csrf(token = layout.csrfToken())

      @if(admin)
        @template.tag.field(form = form, name = "email", label = "邮箱",
                            type = "email", required = true,
                            help = "管理员修改邮箱不需要邮件确认")
        @template.tag.field(form = form, name = "username", label = "用户名",
                            required = true)
        @template.tag.checkbox(form = form, name = "confirmed",
                               label = "已确认邮箱")

        <div class="mb-3">
          <label class="form-label" for="f-roleId">角色</label>
          <select class="form-select" id="f-roleId" name="roleId">
            @for(var role : roles)
              <%-- 智能属性：值为 false 时整个 selected 属性不输出 --%>
              <option value="${role.getId()}"
                      selected="${String.valueOf(role.getId()).equals(form.value("roleId"))}">
                ${role.getName()}
              </option>
            @endfor
          </select>
        </div>
      @endif

      @template.tag.field(form = form, name = "name", label = "真名")
      @template.tag.field(form = form, name = "location", label = "所在地")
      @template.tag.textarea(form = form, name = "aboutMe", label = "自我介绍",
                             rows = 5)

      <button class="btn btn-primary" type="submit">保存</button>
      <a class="btn btn-link"
         href="${admin ? Routes.user(targetUser.getUsername()) : Routes.user(layout.currentUser().getUsername())}">
        取消
      </a>
    </form>
  </div>
`)

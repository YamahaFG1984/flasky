@import gg.jte.Content
@import com.example.javalinky.web.Layout
@import com.example.javalinky.model.Permission
@import com.example.javalinky.web.Routes

@param Layout layout
@param Content content
@param Content extraScripts = null

<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${layout.title()} - Javalinky</title>
  <link rel="icon" href="/favicon.ico">
  <link rel="stylesheet"
        href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
  <link rel="stylesheet" href="/css/app.css">
</head>
<%-- data-authenticated 让 app.js 知道要不要连 SSE（第 21 章）--%>
<body data-authenticated="${layout.isAuthenticated() ? "1" : null}">

<nav class="navbar navbar-expand-lg navbar-dark bg-dark">
  <div class="container">
    <a class="navbar-brand" href="/">Javalinky</a>
    <button class="navbar-toggler" type="button"
            data-bs-toggle="collapse" data-bs-target="#nav">
      <span class="navbar-toggler-icon"></span>
    </button>
    <div class="collapse navbar-collapse" id="nav">
      <ul class="navbar-nav me-auto">
        <li class="nav-item"><a class="nav-link" href="${Routes.INDEX}">首页</a></li>
        @if(layout.isAuthenticated())
          <li class="nav-item">
            <a class="nav-link" href="${Routes.user(layout.currentUser().getUsername())}">我的主页</a>
          </li>
        @endif
        @if(layout.can(Permission.MODERATE))
          <li class="nav-item"><a class="nav-link" href="${Routes.MODERATE}">协管评论</a></li>
        @endif
      </ul>

      <ul class="navbar-nav">
        @if(layout.isAuthenticated())
          <li class="nav-item dropdown">
            <a class="nav-link dropdown-toggle" href="#" data-bs-toggle="dropdown">
              ${layout.currentUser().getUsername()}
            </a>
            <ul class="dropdown-menu dropdown-menu-end">
              <li><a class="dropdown-item" href="${Routes.CHANGE_PASSWORD}">修改密码</a></li>
              <li><a class="dropdown-item" href="${Routes.CHANGE_EMAIL}">修改邮箱</a></li>
              <li><hr class="dropdown-divider"></li>
              <li>
                <%-- 登出用 POST，不用 GET --%>
                <form method="post" action="${Routes.LOGOUT}" class="px-3 py-1">
                  @template.tag.csrf(token = layout.csrfToken())
                  <button class="btn btn-sm btn-outline-secondary w-100" type="submit">登出</button>
                </form>
              </li>
            </ul>
          </li>
        @else
          <li class="nav-item"><a class="nav-link" href="${Routes.LOGIN}">登录</a></li>
          <li class="nav-item"><a class="nav-link" href="${Routes.REGISTER}">注册</a></li>
        @endif
      </ul>
    </div>
  </div>
</nav>

<div class="container py-4">

  @for(var flash : layout.flashes())
    <div class="alert alert-${flash.level()} alert-dismissible fade show" role="alert">
      ${flash.message()}
      <button type="button" class="btn-close" data-bs-dismiss="alert"
              aria-label="关闭"></button>
    </div>
  @endfor

  ${content}
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<script src="/js/app.js"></script>
@if(extraScripts != null)
  ${extraScripts}
@endif

</body>
</html>

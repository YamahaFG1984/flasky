@import gg.jte.Content
@import com.example.javalinky.web.Layout

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
<body>

<nav class="navbar navbar-expand-lg navbar-dark bg-dark">
  <div class="container">
    <a class="navbar-brand" href="/">Javalinky</a>
    <button class="navbar-toggler" type="button"
            data-bs-toggle="collapse" data-bs-target="#nav">
      <span class="navbar-toggler-icon"></span>
    </button>
    <div class="collapse navbar-collapse" id="nav">
      <ul class="navbar-nav me-auto">
        <li class="nav-item"><a class="nav-link" href="/">首页</a></li>
      </ul>
      <ul class="navbar-nav">
        <%-- 第 8 章在这里放登录/账户菜单 --%>
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

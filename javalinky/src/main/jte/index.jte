@import com.example.javalinky.model.Permission
@import com.example.javalinky.web.FormView
@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.page.PostListPage

@param Layout layout
@param FormView form
@param PostListPage posts

@template.layout.base(layout = layout, content = @`
  <div class="p-4 mb-4 bg-body-tertiary rounded-3">
    <h1 class="h3">
      @if(layout.isAuthenticated())
        你好，${layout.currentUser().getUsername()}！
      @else
        欢迎来到 Javalinky
      @endif
    </h1>
    <p class="text-secondary mb-0">用 Javalin + Hibernate 写的社交博客。</p>
  </div>

  <%-- 不用判断是否登录，can() 自己处理了 --%>
  @if(layout.can(Permission.WRITE))
    <form method="post" action="/post" novalidate class="mb-4">
      @template.tag.csrf(token = layout.csrfToken())
      @template.tag.markdown_editor(form = form, name = "body", label = "想说点什么？")
      <button class="btn btn-primary" type="submit">发布</button>
    </form>
  @endif

  @template.tag.posts(layout = layout, posts = posts)
  @template.tag.pagination(page = posts.page(), baseUrl = "/")
`, extraScripts = @`
  <link rel="stylesheet"
        href="https://cdn.jsdelivr.net/npm/easymde@2.18.0/dist/easymde.min.css">
  <script src="https://cdn.jsdelivr.net/npm/easymde@2.18.0/dist/easymde.min.js"></script>
  <script>
    document.querySelectorAll('[data-markdown-editor]').forEach(function (el) {
      new EasyMDE({
        element: el,
        spellChecker: false,
        status: false,
        placeholder: el.placeholder,
        toolbar: ['bold', 'italic', 'heading', '|',
                  'quote', 'unordered-list', 'ordered-list', 'code', '|',
                  'link', 'preview']
      });
    });
  </script>
`)

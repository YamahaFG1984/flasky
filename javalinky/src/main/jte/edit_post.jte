@import com.example.javalinky.web.FormView
@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes

@param Layout layout
@param FormView form
@param long postId

@template.layout.base(layout = layout, content = @`
  <div class="col-lg-8">
    <h1 class="h3 mb-4">编辑文章</h1>

    <form method="post" action="${Routes.editPost(postId)}" novalidate>
      @template.tag.csrf(token = layout.csrfToken())
      @template.tag.markdown_editor(form = form, name = "body", label = "正文")

      <button class="btn btn-primary" type="submit">保存</button>
      <a class="btn btn-link" href="${Routes.post(postId)}">取消</a>
    </form>
  </div>
`, extraScripts = @`
  <link rel="stylesheet"
        href="https://cdn.jsdelivr.net/npm/easymde@2.18.0/dist/easymde.min.css">
  <script src="https://cdn.jsdelivr.net/npm/easymde@2.18.0/dist/easymde.min.js"></script>
  <script>
    document.querySelectorAll('[data-markdown-editor]').forEach(function (el) {
      new EasyMDE({ element: el, spellChecker: false, status: false });
    });
  </script>
`)

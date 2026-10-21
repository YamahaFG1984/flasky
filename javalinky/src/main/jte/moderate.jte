@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes
@import com.example.javalinky.web.page.CommentListPage

@param Layout layout
@param CommentListPage comments

@template.layout.base(layout = layout, content = @`
  <div class="pb-3 mb-4 border-bottom">
    <h1 class="h3">协管评论</h1>
    <p class="text-secondary mb-0">
      共 ${comments.page().total()} 条评论，最新的在前。
    </p>
  </div>

  @template.tag.comments(layout = layout, comments = comments,
                         moderationView = true)

  @template.tag.pagination(page = comments.page(), baseUrl = Routes.MODERATE)
`)

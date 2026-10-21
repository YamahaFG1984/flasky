@import com.example.javalinky.model.Permission
@import com.example.javalinky.web.FormView
@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes
@import com.example.javalinky.web.page.CommentListPage
@import com.example.javalinky.web.page.PostListPage

@param Layout layout
@param PostListPage post
@param CommentListPage comments
@param FormView form

@template.layout.base(layout = layout, content = @`
  <%-- 单篇文章复用列表那套渲染，保证样式一致 --%>
  @template.tag.posts(layout = layout, posts = post)

  <h2 class="h5 mt-4" id="comments">评论（${comments.page().total()}）</h2>

  @if(layout.can(Permission.COMMENT))
    <form class="my-3" id="comment-form" method="post"
          action="${Routes.comments(comments.postId())}" novalidate>
      @template.tag.csrf(token = layout.csrfToken())
      @template.tag.textarea(form = form, name = "body", label = "说两句", rows = 3)
      <button class="btn btn-primary btn-sm" type="submit">发表评论</button>
    </form>
  @elseif(!layout.isAuthenticated())
    <p class="text-secondary small my-3">
      <a href="${Routes.LOGIN}">登录</a> 之后才能评论。
    </p>
  @endif

  @template.tag.comments(layout = layout, comments = comments)
  @template.tag.pagination(page = comments.page(),
                           baseUrl = Routes.post(comments.postId()),
                           fragment = "#comments")
`)

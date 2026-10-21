@import com.example.javalinky.util.Gravatar
@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes
@import com.example.javalinky.web.page.CommentListPage

@param Layout layout
@param CommentListPage comments
@param boolean moderationView = false

<ul class="list-unstyled">
@for(var c : comments.page().items())
  <li class="d-flex gap-3 py-3 border-bottom ${c.disabled() ? "opacity-50" : ""}">
    <img class="rounded" width="32" height="32" loading="lazy"
         src="${c.authorAvatar().url(32)}"
         alt="${c.authorUsername()} 的头像">

    <div class="flex-grow-1">
      <div class="small text-secondary">
        <a class="link-secondary" href="${Routes.user(c.authorUsername())}">
          ${c.authorUsername()}
        </a>
        · @template.tag.timeago(at = c.timestamp())
        @if(c.disabled())
          <span class="badge text-bg-warning">已隐藏</span>
        @endif
      </div>

      <div class="comment-body">$unsafe{c.bodyHtml()}</div>

      @if(comments.canModerate())
        <form class="mt-2" method="post"
              action="${c.disabled()
                        ? Routes.enableComment(c.id())
                        : Routes.disableComment(c.id())}">
          @template.tag.csrf(token = layout.csrfToken())
          @if(moderationView)
            <input type="hidden" name="page" value="${comments.page().page()}">
          @endif
          <button class="btn btn-sm ${c.disabled()
                    ? "btn-outline-success" : "btn-outline-danger"}"
                  type="submit">
            ${c.disabled() ? "恢复显示" : "隐藏这条"}
          </button>
        </form>
      @endif
    </div>
  </li>
@else
  <li class="text-secondary py-4">还没有评论，来说两句？</li>
@endfor
</ul>

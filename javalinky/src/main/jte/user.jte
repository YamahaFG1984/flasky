@import com.example.javalinky.web.*
@import com.example.javalinky.web.page.PostListPage
@import com.example.javalinky.web.page.UserProfilePage

@param Layout layout
@param UserProfilePage page
@param PostListPage posts

@template.layout.base(layout = layout, content = @`
  <div class="d-flex gap-4 pb-4 mb-4 border-bottom flex-wrap">

    <img class="rounded" src="${page.avatarUrl()}" width="128" height="128"
         alt="${page.username()} 的头像">

    <div class="flex-grow-1">
      <h1 class="h3">${page.username()}</h1>

      @if(page.hasName() || page.hasLocation())
        <p class="text-secondary mb-2">
          @if(page.hasName())${page.name()}@endif
          @if(page.hasName() && page.hasLocation()) · @endif
          @if(page.hasLocation())
            <a class="link-secondary"
               href="https://www.openstreetmap.org/search?query=${page.location()}">
              ${page.location()}
            </a>
          @endif
        </p>
      @endif

      @if(page.hasEmail())
        <p class="small"><a href="mailto:${page.email()}">${page.email()}</a>
           <span class="badge text-bg-danger">仅管理员可见</span></p>
      @endif

      @if(page.hasAboutMe())
        <p>${page.aboutMe()}</p>
      @endif

      <p class="text-secondary small mb-2">
        注册于 @template.tag.timeago(at = page.memberSince(), relative = false)
        · 最后活跃 @template.tag.timeago(at = page.lastSeen())
      </p>

      <p class="small">
        <strong>${page.postCount()}</strong> 篇文章 ·
        <strong>${page.commentCount()}</strong> 条评论
      </p>

      <div class="d-flex gap-2 flex-wrap">
        @if(page.isSelf())
          <a class="btn btn-sm btn-outline-secondary" href="${Routes.EDIT_PROFILE}">编辑资料</a>
        @endif
        @if(layout.isAdmin())
          <a class="btn btn-sm btn-outline-danger"
             href="${Routes.adminEditProfile(page.id())}">编辑资料（管理员）</a>
        @endif
      </div>
    </div>
  </div>

  <h2 class="h5">${page.username()} 的文章</h2>
  @template.tag.posts(layout = layout, posts = posts)
  @template.tag.pagination(page = posts.page(), baseUrl = Routes.user(page.username()))
`)

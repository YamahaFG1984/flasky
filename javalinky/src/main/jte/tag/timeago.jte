@import com.example.javalinky.web.Layout
@import java.time.Instant

@param Layout layout
@param Instant at
@param boolean relative = true

<%-- datetime 属性里永远是 UTC 的 ISO 串（机器读），
     显示出来的才是用户时区的本地时间（人读）。第 23 章 --%>
<time datetime="${layout.formats().iso(at)}"
      title="${layout.formats().dateTime(at)}">
    ${relative ? layout.formats().relative(at, Instant.now()) : layout.formats().dateTime(at)}
</time>

@import java.time.Instant

@param Instant at
@param boolean relative = true

<time datetime="${at.toString()}"
      data-style="${relative ? "relative" : "absolute"}">
    ${at.toString()}
</time>

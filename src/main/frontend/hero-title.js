customElements.define('hero-title', class extends HTMLElement {
  connectedCallback() {
    this.attachShadow({ mode: 'open' });
    this.shadowRoot.innerHTML = `
      <style>
        :host {
          display: contents;
        }

        .container {
          /* Create a stacking context for the pseudo element. Without a top offset it won't do anything else */
          position: sticky;
          z-index: 1;
          display: flex;
          gap: var(--vaadin-gap-s);
          padding-top: calc(var(--vaadin-padding-xl) + var(--vaadin-padding-xs));
          margin-top: var(--hero-title-spacing, 3rem);
          margin-bottom: var(--hero-title-spacing, 2.5rem);
          transition: margin-top, margin-bottom;
          transition-duration: 400ms;
          transition-timing-function: cubic-bezier(0.66, 0, 0.34, 1);

          &::before {
            content: "";
            position: absolute;
            inset-inline: calc(var(--view-padding) * -1);
            top: -100px;
            bottom: -20px;
            --_translucent: color-mix(in srgb, var(--aura-background-color) 70%, transparent);
            background: linear-gradient(var(--aura-background-color) 100px, var(--_translucent) 110px, var(--_translucent) calc(100% - 60px), transparent);
            backdrop-filter: blur(8px);
            mask-image: linear-gradient(#000 calc(100% - 40px), transparent);
            opacity: 0;
            pointer-events: none;
          }
        }

        /* Enable sticky positioning on browsers that support scroll animation-timeline */
        @supports (animation-timeline: scroll()) {
          .container {
            top: 0;
          }
        }

        .deco, .titles {
          font-weight: 900;
          font-style: italic;
          font-stretch: 90%;
          font-width: 90%;
          letter-spacing: -1%;
          line-height: 1;
          text-transform: uppercase;
        }

        .deco, .subtitle {
          color: var(--aura-accent-color);
        }

        .deco {
          position: sticky;
          top: 0;
          z-index: -1;
          pointer-events: none;
          font-size: 14rem;
          line-height: 0.9;
          opacity: 0.2;
          inset-inline-start: 0.5rem;
          letter-spacing: -3%;
          white-space: nowrap;
          box-sizing: border-box;
          padding-inline-end: 0.2em;
          max-width: fit-content;
          overflow: hidden;
          background: radial-gradient(68% 70% at 46% 50%, var(--aura-accent-color) 0%, transparent 100%);
          background-clip: text;
          color: transparent;
          background-repeat: no-repeat;
          animation: var(--hero-title-animation, sweep 3s cubic-bezier(0.66, 0, 0.34, 1) backwards, fade 1s linear backwards, slide2 3.6s 0.2s cubic-bezier(0.33, 1, 0.68, 1) backwards);
          margin-bottom: -1lh;
        }

        @-moz-document url-prefix() {
          .deco {
            opacity: 0.4;
          }
        }

        @keyframes fade {
          0% {
            opacity: 0;
          }
        }

        @keyframes sweep {
          0% {
            background-position: -400px;
          }
        }

        @keyframes slide2 {
          0% {
            translate: -24px 0;
          }
        }

        @keyframes slide {
          0% {
            translate: -7px 0;
          }
        }

        .titles {
          flex: 1;
          filter: drop-shadow(1px 1px 0.5px light-dark(transparent, rgba(0, 0, 0, 0.3)));
          scale: var(--hero-title-scale, 1);
          transform-origin: 0 0;
          translate: 0% var(--hero-title-translate, 0%);
          transition: scale, translate;
          transition-duration: 400ms;
          transition-timing-function: cubic-bezier(0.66, 0, 0.34, 1);
          text-wrap: balance;
        }

        .title {
          width: fit-content;
          font-size: 2.25rem;
          text-indent: 0.1em;
          display: block;
          background: radial-gradient(67% 200% at 35% 50%, #fff 0%, transparent 100%) #333;
          background-repeat: no-repeat;
          background-clip: text;
          animation: var(--hero-title-animation, fade 1s backwards, sweep 3s -2s cubic-bezier(0.66, 0, 0.34, 1), fill-color 1s 0.7s backwards);
          padding-inline-end: 0.2em;
        }

        .subtitle {
          width: fit-content;
          font-size: 1.4rem;
          display: block;
          background: radial-gradient(67% 200% at 35% 50%, var(--aura-accent-color) 0%, transparent 100%) color-mix(in srgb, var(--aura-accent-color) 30%, #000);
          background-repeat: no-repeat;
          background-clip: text;
          animation: var(--hero-title-animation, fade 1.2s 0.2s backwards, sweep 3s -1.3s cubic-bezier(0.66, 0, 0.34, 1), fill-color 1s 1s backwards);
          padding-inline-end: 0.2em;
          transform-origin: inherit;
          scale: min(1, var(--hero-title-scale) * 1.2);
          transition: scale;
          transition-duration: 400ms;
          transition-timing-function: cubic-bezier(0.66, 0, 0.34, 1);
        }

        @keyframes fill-color {
          0% {
            color: transparent;
          }
        }

        @media (width < 500px), (height < 500px) {
          .container {
            padding-top: var(--view-padding);
          }

          .deco {
            font-size: 10rem;
          }

          .title {
            font-size: 1.75rem;
          }

          .subtitle {
            font-size: 1.125rem;
          }
        }

        @supports (animation-timeline: scroll()) {
          .titles {
            animation: var(--hero-title-scroll-animation, --hero-title-shrink linear both);
            animation-timeline: scroll(nearest block);
            animation-range: 30px 90px;
          }

          .container::before {
            animation: --hero-title-mask-fade linear both;
            animation-timeline: scroll(nearest block);
            animation-range: 1px 40px;
          }
        }

        @keyframes --hero-title-shrink {
          0% {
            scale: 1;
          }
          100% {
            scale: 0.7;
          }
        }

        @keyframes --hero-title-mask-fade {
          0% {
            opacity: 0;
          }
          100% {
            opacity: 1;
          }
        }
      </style>
      <div class="deco" aria-hidden="true"></div>
      <div class="container">
        <slot name="prefix"></slot>
        <div class="titles">
          <slot name="title" class="title"></slot>
          <slot name="subtitle" class="subtitle"></slot>
        </div>
        <slot name="suffix"></slot>
      </div>
    `;

    const deco = this.shadowRoot.querySelector('.deco');
    const decoText = this.hasAttribute('deco-text') ? this.getAttribute('deco-text') : this.querySelector('[slot="title"]').textContent;
    deco.textContent = decoText;
  }
});

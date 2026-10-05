customElements.define('hero-title', class extends HTMLElement {
  connectedCallback() {
    this.attachShadow({ mode: 'open' });
    this.shadowRoot.innerHTML = `
      <style>
        :host {
          display: grid;
          font-weight: 900;
          font-style: italic;
          font-stretch: 90%;
          font-width: 90%;
          letter-spacing: -1%;
          line-height: 1;
          text-transform: uppercase;
          position: relative;
          max-width: 100%;
          width: 100%;
        }

        .deco, .front {
          grid-row: 1;
          grid-column: 1;
          scale: var(--hero-title-scale, 0.71);
          transform-origin: 0% 0%;
          transition-property: scale, transform;
          transition-duration: 400ms;
          transition-timing-function: cubic-bezier(0.66, 0, 0.34, 1);
        }

        .deco, .subtitle {
          color: var(--aura-accent-color);
        }

        .deco {
          transition-delay: 100ms;
          transition-duration: 320ms;
          pointer-events: none;
          position: absolute;
          font-size: 14rem;
          opacity: 0.2;
          top: -2.2rem;
          inset-inline-start: 0.5rem;
          letter-spacing: -3%;
          white-space: nowrap;
          box-sizing: border-box;
          padding-inline-end: 0.2em;
          max-width: 100%;
          overflow: hidden;
          background: radial-gradient(68% 70% at 46% 50%, var(--aura-accent-color) 0%, transparent 100%);
          background-clip: text;
          color: transparent;
          background-repeat: no-repeat;
          animation: var(--hero-title-animation, sweep 3s 0.7s cubic-bezier(0.66, 0, 0.34, 1) backwards, fade 1s 0.7s linear backwards, slide2 2s 0.7s cubic-bezier(0.33, 1, 0.68, 1) backwards);
          transform: translate(0, var(--hero-title-deco-translate, 0));
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
            translate: -15px 0;
          }
        }

        @keyframes slide {
          0% {
            translate: -7px 0;
          }
        }

        .front {
          z-index: 1;
          filter: drop-shadow(1px 1px 0.5px light-dark(transparent, rgba(0, 0, 0, 0.3)));
          transform: translate(0, var(--hero-title-translate, 0));
        }

        .title {
          width: fit-content;
          font-size: 2.25rem;
          text-indent: 0.1em;
          display: block;
          background: radial-gradient(67% 200% at 35% 50%, #fff 0%, transparent 100%) #333;
          background-repeat: no-repeat;
          background-clip: text;
          animation: var(--hero-title-animation, fade 1s backwards, sweep 3s -2s cubic-bezier(0.66, 0, 0.34, 1), fill-color 1s 1s backwards);
          padding-inline-end: 0.2em;
        }

        .subtitle {
          width: fit-content;
          font-size: 1.4rem;
          display: block;
          background: radial-gradient(67% 200% at 35% 50%, var(--aura-accent-color) 0%, transparent 100%) color-mix(in srgb, var(--aura-accent-color) 30%, #000);
          background-repeat: no-repeat;
          background-clip: text;
          animation: var(--hero-title-animation, fade 1.2s 0.2s backwards, sweep 3s -1.3s cubic-bezier(0.66, 0, 0.34, 1), fill-color 1s 1.3s backwards);
          padding-inline-end: 0.2em;
        }

        @keyframes fill-color {
          0% {
            color: transparent;
          }
        }
      </style>
      <div class="deco" aria-hidden="true"></div>
      <div class="front">
        <slot name="title" class="title"></slot>
        <slot name="subtitle" class="subtitle"></slot>
      </div>
    `;

    const deco = this.shadowRoot.querySelector('.deco');
    const decoText = this.hasAttribute('deco-text') ? this.getAttribute('deco-text') : this.querySelector('[slot="title"]').textContent;
    deco.textContent = decoText;
  }
});

import { ThreeJsScene } from "./ThreeJsScene";
import { WebGLShaderCanvas } from "./WebGLShaderCanvas";

function BrandBlock() {
  return (
    <div className="z-20 flex flex-col items-center text-center">
      <h1 className="font-display-lg mb-4 select-none text-8xl leading-none tracking-tighter text-foreground md:text-[9.5rem]">
        Blaie
      </h1>
      <p className="font-anthropic-sans mt-2 select-none whitespace-nowrap text-xs uppercase tracking-[0.2em] text-muted-foreground opacity-80 md:text-sm">
        Capture everything. Structure instantly.
      </p>
    </div>
  );
}

export function LandingHero() {
  return (
    <main className="grid w-full h-full">
      <div className="factory-zone relative z-20">
        <BrandBlock />
      </div>

      <div className="particle-dust" />
      <div className="absolute inset-0 z-0 h-full w-full pointer-events-none">
        <WebGLShaderCanvas />
      </div>
      <div className="absolute inset-0 z-10 h-full w-full pointer-events-none">
        <ThreeJsScene />
      </div>
      <div className="core-bloom" />
    </main>
  );
}

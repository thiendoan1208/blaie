type AuthHeadingProps = {
  eyebrow: string;
  title: string;
  description: string;
};

export function AuthHeading({ eyebrow, title, description }: AuthHeadingProps) {
  return (
    <div className="mb-9">
      <p className="mb-3 font-mono text-[12px] font-bold tracking-widest text-black uppercase">
        {eyebrow}
      </p>
      <h1 className="font-anthropic-serif text-[2.35rem] leading-tight font-[330] tracking-[-0.045em] text-ivory-text sm:text-[2.65rem]">
        {title}
      </h1>
      <p className="mt-3 max-w-md text-sm leading-6 text-warm-slate">
        {description}
      </p>
    </div>
  );
}

import Image from "next/image";

export function LandingBrandMark() {
  return (
    <>
      <Image
        src="/logo.svg"
        alt=""
        aria-hidden="true"
        width={40}
        height={40}
        className="h-9 w-9 shrink-0 md:h-10 md:w-10"
      />
      <span className="font-display-sm text-[22px] font-normal tracking-tight">
        Blaie
      </span>
    </>
  );
}

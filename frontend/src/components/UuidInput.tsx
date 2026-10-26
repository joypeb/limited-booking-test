"use client";

import { inputClassName } from "./control-styles";

type UuidInputProps = {
  label: string;
  value: string;
  invalid: boolean;
  errorId: string;
  onChange: (value: string) => void;
};

export function UuidInput({ label, value, invalid, errorId, onChange }: UuidInputProps) {
  return (
    <>
      <label className="grid gap-1">{label}
        <input
          className={inputClassName}
          value={value}
          aria-invalid={invalid}
          aria-describedby={invalid ? errorId : undefined}
          onChange={(event) => onChange(event.target.value)}
        />
      </label>
      {invalid && <p id={errorId} role="alert">식별자는 UUID 형식이어야 합니다</p>}
    </>
  );
}

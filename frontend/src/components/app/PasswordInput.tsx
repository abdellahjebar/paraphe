import { EyeIcon, EyeOffIcon } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { COMMON } from '@/lib/texts.fr'
import { cn } from '@/lib/utils'

type PasswordInputProps = Omit<React.ComponentProps<typeof Input>, 'type'> & {
  /** Name of the button that shows or hides the password. */
  toggleLabel?: string
}

/**
 * A password field with a control to show what was typed (decision 42). Pasting and password managers are
 * never blocked (WCAG 3.3.8): this is a plain input with a button next to it.
 */
export function PasswordInput({
  className,
  toggleLabel = COMMON.showPassword,
  ...props
}: PasswordInputProps) {
  const [visible, setVisible] = useState(false)

  return (
    <div className="relative">
      <Input {...props} type={visible ? 'text' : 'password'} className={cn('pr-12', className)} />
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label={toggleLabel}
        aria-pressed={visible}
        onClick={() => setVisible((current) => !current)}
        className="absolute top-1/2 right-1.5 -translate-y-1/2"
      >
        {visible ? <EyeOffIcon aria-hidden="true" /> : <EyeIcon aria-hidden="true" />}
      </Button>
    </div>
  )
}

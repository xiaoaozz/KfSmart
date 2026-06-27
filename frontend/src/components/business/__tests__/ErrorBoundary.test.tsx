import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import React from 'react'
import ErrorBoundary from '../ErrorBoundary'

// Silence expected console.error from the thrown error
beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => {
  vi.restoreAllMocks()
})

function ThrowOnRender({ message }: { message: string }): React.ReactNode {
  throw new Error(message)
}

describe('ErrorBoundary', () => {
  it('renders children when there is no error', () => {
    render(
      <ErrorBoundary>
        <span>all good</span>
      </ErrorBoundary>,
    )
    expect(screen.getByText('all good')).toBeInTheDocument()
  })

  it('shows error title when a child throws', () => {
    render(
      <ErrorBoundary>
        <ThrowOnRender message="boom" />
      </ErrorBoundary>,
    )
    expect(screen.getByText('页面渲染出错')).toBeInTheDocument()
  })

  it('shows the thrown error message as subtitle', () => {
    render(
      <ErrorBoundary>
        <ThrowOnRender message="something broke" />
      </ErrorBoundary>,
    )
    expect(screen.getByText('something broke')).toBeInTheDocument()
  })

  it('renders a custom fallback instead of default error UI', () => {
    render(
      <ErrorBoundary fallback={<div>custom error view</div>}>
        <ThrowOnRender message="any" />
      </ErrorBoundary>,
    )
    expect(screen.getByText('custom error view')).toBeInTheDocument()
    expect(screen.queryByText('页面渲染出错')).not.toBeInTheDocument()
  })

  it('recovers after clicking the retry button', async () => {
    const user = userEvent.setup()

    // Use a flag to control whether the child throws
    let shouldThrow = true
    function ConditionalThrow() {
      if (shouldThrow) throw new Error('recoverable')
      return <span>recovered</span>
    }

    render(
      <ErrorBoundary>
        <ConditionalThrow />
      </ErrorBoundary>,
    )

    expect(screen.getByText('页面渲染出错')).toBeInTheDocument()

    // Stop throwing before clicking retry — Error Boundary's re-render will pick it up
    shouldThrow = false
    // antd Button inserts a space between CJK chars: text is "重 试"
    await user.click(screen.getByRole('button', { name: /重/ }))

    expect(screen.getByText('recovered')).toBeInTheDocument()
  })
})

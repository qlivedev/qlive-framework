/**
 * Returns a promise that resolves after the given number of milliseconds
 *
 * @param delay delay in milliseconds
 */
export default function delay(delay: number): Promise<void> {
    return new Promise(
        resolve => setTimeout(resolve, delay)
    );
}

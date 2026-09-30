/**
 * Find the root container in a QLive template.
 *
 * @param id    id of element to find, default is "root", used by QLive for the root container
 */
export default function findRoot(id = "root") : HTMLElement
{
    const container = document.getElementById(id);
    if (!container){
        throw new Error("View must have a #root element")
    }
    return container
}

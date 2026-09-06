export default function findRoot(id = "root") : HTMLElement
{
    const container = document.getElementById(id);
    if (!container){
        throw new Error("View must have a #root element")
    }
    return container
}

var PointsTo = {
    columns: ["localVal", "value"],
    rows: [
        [1, "/ParityAnalysis::Parity.Odd(())"],
        [2, "/ParityAnalysis::Parity.Even(())"],
        [3, "/ParityAnalysis::Parity.Odd(())"],
        [7, "/ParityAnalysis::Parity.Odd(())"],
        [8, "/ParityAnalysis::Parity.Top(())"]
    ]
};

var SumOp = {
    columns: ["localVal", "value"],
    rows: [
        [1, "Buba"],
    ]
};

var Phases = [
    {
        phase: "Parsing",
        time: 243
    },
    {
        phase: "Typing",
        time: 2543
    }

];

var Indexes = [
    {
        collection: "/Pt",
        index: "{variable, target}",
        hits: 2439278
    },
    {
        collection: "/CFG",
        index: "{label} ",
        hits: 959689
    },
    {
        collection: "/Phi",
        index: "{label} ",
        hits: 959565
    }
];

var Queries = [
    {
        rule: "SUBefore(l2,a,t) :- CFG(l1,l2), SUAfter(l1,a,t).",
        hitcount: 959690,
        time: 8714,
        location: "101"
    },

    {
        rule: "SUAfter(l,a,t) :- SUBefore(l,a,t), Phi(l). ",
        hitcount: 956864,
        time: 6597,
        location: "115"
    },

    {
        rule: "SUAfter(l,a,t) :- SUBefore(l,a,t), killNot(a, k), Kill(l,k). ",
        hitcount: 958449,
        time: 5562,
        location: "113"
    }
];

var Relations = ["SumOp"];
var Lattices = ["VarPointsTo"];

/**
 * Main Application entry point.
 */
var App = React.createClass({

    getInitialState: function () {
        return {page: {name: "default"}};
    },

    changePage: function (page) {
        this.setState({page: page})
    },

    defaultPage: function () {
        return (   <RelationPage name="VarPointsTo" table={PointsTo}/>);
    },

    PhasesPage: function () {
        return (
            <div>
                <Head name="Performance / Phases"/>
            </div>
        );
    },

    render: function () {
        var page = null;
        var pageName = this.state.page.name;
        if (pageName === "performance/phases") {
            page = <PhasesPage />
        } else if (pageName === "performance/indexes") {
            page = <IndexesPage />
        } else if (pageName === "performance/queries") {
            page = <QueriesPage />
        } else {
            page = this.defaultPage();
        }

        return (
            <div>
                <Menu changePage={this.changePage} relations={Relations} lattices={Lattices}/>
                {page}
            </div>
        );
    }
});

/**
 * Menu component.
 */
var Menu = React.createClass({
    propTypes: {
        changePage: React.PropTypes.func.isRequired,
        relations: React.PropTypes.array.isRequired,
        lattices: React.PropTypes.array.isRequired
    },

    render: function () {
        return (
            <nav className="navbar navbar-default">
                <div className="container-fluid">
                    <div className="navbar-header">
                        <a className="navbar-brand" href="#">Flix Debugger</a>
                    </div>

                    <ul className="nav navbar-nav">
                        <li className="dropdown">
                            <a href="#" className="dropdown-toggle" data-toggle="dropdown">
                                Minimal Model <span className="caret"></span>
                            </a>
                            <ul className="dropdown-menu">
                                {this.props.relations.map(relation => {
                                    return <li key={relation} onClick={() => this.props.changePage({relation})}>
                                        <a href="#">{relation}</a>
                                    </li>
                                })}

                                <li role="separator" className="divider"></li>

                                {this.props.lattices.map(lattice => {
                                    return <li key={lattice}>
                                        <a href="#">{lattice}</a>
                                    </li>
                                })}
                            </ul>
                        </li>
                    </ul>

                    <ul className="nav navbar-nav">
                        <li className="dropdown">
                            <a href="#" className="dropdown-toggle" data-toggle="dropdown">
                                Performance
                                <className className="caret"></className>
                            </a>
                            <ul className="dropdown-menu">
                                <li>
                                    <a href="#" onClick={() => this.props.changePage({name: "performance/phases"})}>Phases</a>
                                </li>
                                <li>
                                    <a href="#" onClick={() => this.props.changePage({name: "performance/indexes"})}>Indexes</a>
                                </li>
                                <li>
                                    <a href="#" onClick={() => this.props.changePage({name: "performance/queries"})}>Queries</a>
                                </li>
                            </ul>
                        </li>
                    </ul>

                    <ul className="nav navbar-nav navbar-right">
                        <li className="bg-success">
                            <a href="#"><span className="glyphicon glyphicon-fire"></span> Running</a>
                        </li>

                        <li>
                            <a href="#"> <span className="glyphicon glyphicon-refresh"></span> Refresh</a>
                        </li>
                    </ul>
                </div>
            </nav>
        );
    }
});

/**
 * Relation page.
 */
var RelationPage = React.createClass({
    propTypes: {
        name: React.PropTypes.string.isRequired,
        table: React.PropTypes.object.isRequired
    },
    render: function () {
        return (
            <div>
                <Head name={this.props.name}/>
                <Table table={this.props.table}/>
            </div>
        );
    }
});

/**
 * Phases page.
 */
var PhasesPage = React.createClass({
    render: function () {
        var table = {
            columns: ["Name", "Time"],
            rows: Phases.map(row =>
                    [row["phase"], row["time"]]
            )
        };

        return (
            <div>
                <Head name="Performance / Phases"/>
                <Table table={table}/>
            </div>
        );
    }
});

/**
 * Indexes page.
 */
var IndexesPage = React.createClass({
    render: function () {
        var table = {
            columns: ["Collection", "Index", "Hits"],
            rows: Indexes.map(row =>
                    [row["collection"], row["index"], row["hits"]]
            )
        };

        return (
            <div>
                <Head name="Performance / Indexes"/>
                <Table table={table}/>
            </div>
        );
    }
});

/**
 * Queries page.
 */
var QueriesPage = React.createClass({
    render: function () {
        var table = {
            columns: ["Source Location", "Rule", "Hitcount", "Total Time (msec)", "Time / Operation (usec)"],
            rows: Queries.map(row =>
                    [row["location"], row["rule"], row["hitcount"], row["time"], 1000 * row["time"] / row["hitcount"]]
            )
        };

        return (
            <div>
                <Head name="Performance / Queries"/>
                <Table table={table}/>
            </div>
        );
    }
});


/**
 * Head component.
 */
var Head = React.createClass({
    propTypes: {
        name: React.PropTypes.string.isRequired
    },
    render: function () {
        return (
            <div className="page-header">
                <h1>{this.props.name}</h1>
            </div>
        );
    }
});

/**
 * Table component.
 */
var Table = React.createClass({
    propTypes: {
        table: React.PropTypes.shape({
            columns: React.PropTypes.array.isRequired,
            rows: React.PropTypes.array.isRequired
        })
    },
    render: function () {
        return (
            <table className="table table-striped table-condense">
                <TableHeader columns={this.props.table.columns}/>
                <TableBody rows={this.props.table.rows}/>
            </table>
        );
    }
});

/**
 * Table Header component.
 */
var TableHeader = React.createClass({
    propTypes: {
        columns: React.PropTypes.array.isRequired
    },
    render: function () {
        return (
            <thead>
            <tr>
                {this.props.columns.map(function (column) {
                    return <th key={column}>{column}</th>
                })}
            </tr>
            </thead>
        );
    }
});

/**
 * Table Body component.
 */
var TableBody = React.createClass({
    propTypes: {
        rows: React.PropTypes.array.isRequired
    },
    render: function () {
        return (
            <tbody>
            {this.props.rows.map(function (row) {
                return <TableRow key={row} row={row}/>
            })}
            </tbody>
        );
    }
});

/**
 * Table Row component.
 */
var TableRow = React.createClass({
    propTypes: {
        row: React.PropTypes.array.isRequired
    },
    render: function () {
        return (
            <tr>
                {this.props.row.map(function (elm) {
                    return <td>{elm}</td>
                })}
            </tr>
        );
    }
});


/**
 * Render app when the page is ready.
 */
$(document).ready(function () {
    var root = document.getElementById("container");
    ReactDOM.render(React.createElement(App, null), root);
});
